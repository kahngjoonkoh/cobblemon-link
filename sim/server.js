// Battle sidecar: one battle per TCP connection, newline-delimited JSON. Runs Cobblemon's own Showdown build
// (data/cobblemon/showdown.zip, unpacked by tools/build.sh into assets/showdown and mounted at $SHOWDOWN), so HP,
// status and PP carry in and out of battle and bag items work exactly like Cobblemon's.
//   in : {"p1":[set...],"p2":[set...],"pvp":false,"format":"singles"|"doubles"|"triples","skill":0-5 (an NPC trainer: Cobblemon's StrongBattleAI skill)}   then {"choose":"move 1" | "switch 2" | "pass" | "useitem <uuid> <name> <item> [data...]",
//        "side":"p1"}  or  {"forfeit":"p1"}
//   out: {"type":"request","request":{...}}  {"type":"log","lines":[...]}
//        {"type":"state","p1":[...],"p2":[...]}  {"type":"end","winner":"p1"|"p2"|null}
// A set is Showdown's PokemonSet plus Cobblemon's fields: uuid, hp (null = full), status, pp ([current...] or null),
// happiness, ball. p2 (the wild Pokémon) picks random legal moves, unless pvp: then p2 is a player too and its
// requests come out with "side":"p2".
'use strict';
const fs = require('fs');
const net = require('net');
const path = require('path');

const SD = path.resolve(process.env.SHOWDOWN || '/showdown');
const { BattleStream, getPlayerStreams } = require(`${SD}/sim/battle-stream`);
const { Dex } = require(`${SD}/sim/dex`);
const { Cobblemon } = require(`${SD}/sim/cobblemon/cobblemon`);
const { RandomPlayerAI } = require(`${SD}/sim/tools/random-player-ai`);

const PORT = +(process.env.PORT || 9000);
// Showdown's RandomPlayerAI assumes at most two per side (pokemon[i ^ 1]) and throws in triples; let Showdown pick then
const receiveRequest = RandomPlayerAI.prototype.receiveRequest;
RandomPlayerAI.prototype.receiveRequest = function (request) {
  this.lastRequest = request;
  this.tries = 0;
  try { receiveRequest.call(this, request); } catch (e) { this.receiveError(e); }
};
// an invalid AI choice (a fainted target in triples) must not throw: that would take the whole sim process down.
// Pick again at random a few times. ponytail: Showdown's own 'default' crashes in this fork's autoChoose.
RandomPlayerAI.prototype.receiveError = function () {
  if (!this.lastRequest) return;
  if (this.tries++ < 5) {
    try { return receiveRequest.call(this, this.lastRequest); } catch (e) { /* below */ }
  }
  this.choose(fallback(this.lastRequest)); // (its own pick throws with three actives and no fourth Pokémon)
};

/** A choice that is always valid: the first usable move at the foe straight across, or the first free switch. */
function fallback(r) {
  const mons = r.side.pokemon, used = new Set();
  const free = () => { const j = mons.findIndex((p, k) => !p.active && !p.condition.endsWith(' fnt') && !used.has(k)); if (j >= 0) used.add(j); return j; };
  if (r.forceSwitch) return r.forceSwitch.map(f => { const j = f ? free() : -1; return j >= 0 ? `switch ${j + 1}` : 'pass'; }).join(', ');
  const n = r.active.length;
  return r.active.map((a, k) => {
    if (mons[k].condition.endsWith(' fnt')) return 'pass';
    const m = a.moves.findIndex(x => !x.disabled), mv = a.moves[Math.max(0, m)];
    return `move ${Math.max(0, m) + 1}` + (n > 1 && ['normal', 'any', 'adjacentFoe'].includes(mv.target) ? ` ${n - k}` : '');
  }).join(', ');
}
// BattleFormat.GEN_9_SINGLES, as Cobblemon sends it (BattleFormat.toFormatJSON)
const FORMAT = { mod: 'cobblemon', gameType: 'singles', gen: 9, ruleset: ['Obtainable', '+Past', '+Unobtainable'], effectType: 'Format' };
const toID = s => String(s || '').toLowerCase().replace(/[^a-z0-9]+/g, '');

// Cobblemon's bag item effects (data/cobblemon/bag_items/*.js: a JS object literal with use(battle, pokemon, itemId, data))
const bagDir = `${SD}/bag_items`;
if (fs.existsSync(bagDir)) for (const f of fs.readdirSync(bagDir).filter(f => f.endsWith('.js'))) {
  const src = fs.readFileSync(path.join(bagDir, f), 'utf8').replace(/^\s*\/\*[\s\S]*?\*\//, '');
  Cobblemon.registries.bagItem.register(eval(`(${src})`), f.slice(0, -3));
}

/** Cobblemon's BattleRegistry.packTeam: Showdown's packed format with the fork's uuid/HP/status/PP fields. */
function pack(team) {
  const clean = s => String(s || '').replace(/[|,\]]/g, '');
  return team.map(set => {
    // a Cobblemon form Showdown doesn't have (valencian) battles as its base species
    const species = Dex.species.get(set.species).exists ? set.species : set.baseSpecies || set.species;
    const moves = set.moves.map(toID).filter(m => Dex.moves.get(m).exists);
    const pp = moves.map((m, i) => {
      const max = Dex.moves.get(m).pp, cur = (set.pp || [])[set.moves.map(toID).indexOf(m)];
      return `${cur == null ? max : Math.max(0, Math.min(cur, max))}/${max}`;
    });
    const st = s => ['hp', 'atk', 'def', 'spa', 'spd', 'spe'].map(k => (s || {})[k] ?? '').join(',');
    const status = set.status || '';
    return [clean(set.name), toID(species), set.uuid, set.hp == null ? '' : set.hp, status, ['slp', 'frz'].includes(status) ? 2 : -1,
      toID(set.item), toID(set.ability), moves.join(','), pp.join(','), toID(set.nature), st(set.evs), set.gender || '', st(set.ivs),
      set.shiny ? 'S' : '', set.level,
      [set.happiness ?? 70, toID(set.ball || 'pokeball'), '', '', '', ''].join(',')].join('|');
  }).join(']');
}

/**
 * An NPC trainer (Cobblemon's StrongBattleAI, simplified): with probability skill/5 the move doing the most damage to the
 * player's active Pokémon (power x type effectiveness x STAB x accuracy), else a random one.
 * ponytail: no switching strategy, setup or status logic; Cobblemon's AI scores those too.
 */
class TrainerAI extends RandomPlayerAI {
  constructor(stream, battle, skill) { super(stream); this.battle = battle; this.skill = skill; }
  /** Skill 3+: in singles, switch to a benched Pokémon whose best move hits the foe at least twice as hard. */
  receiveRequest(request) {
    const b = this.battle(), me = b && b.p2.active[0], foe = b && b.p1.active[0];
    if (this.skill >= 3 && me && foe && request.active && request.active.length === 1 && !request.forceSwitch
        && !request.active[0].trapped && this.prng.next() < this.skill / 5) {
      const best = p => Math.max(1, ...p.moves.map(m => score(Dex.moves.get(toID(m)), p, foe)));
      const now = best({ moves: request.active[0].moves.map(m => m.move), getTypes: () => me.getTypes() });
      let pick = null, top = now * 2;
      b.p2.pokemon.forEach((p, i) => {
        if (p.isActive || p.fainted || p.hp <= 0) return;
        const v = best({ moves: p.moveSlots.map(m => m.move), getTypes: () => p.getTypes() });
        if (v >= top) { top = v; pick = i + 1; } // side.pokemon is in the request's order too
      });
      if (pick > 0) return this.choose(`switch ${pick}`);
    }
    return super.receiveRequest(request);
  }
  chooseMove(active, moves) {
    const b = this.battle(), me = b && b.p2.active[0], foe = b && b.p1.active[0];
    if (!me || !foe || this.prng.next() >= this.skill / 5) return super.chooseMove(active, moves);
    return moves.map(m => ({ m, score: score(Dex.moves.get(toID(m.move.move)), me, foe) }))
      .sort((a, b) => b.score - a.score)[0].m.choice;
  }
}

function score(move, me, foe) {
  if (!move.exists || move.category === 'Status') return 1;
  const eff = Dex.getImmunity(move.type, foe.getTypes()) ? 2 ** Dex.getEffectiveness(move.type, foe.getTypes()) : 0;
  return (move.basePower || 40) * eff * (me.getTypes().includes(move.type) ? 1.5 : 1) * (move.accuracy === true ? 1 : move.accuracy / 100);
}

function state(side) {
  // reported in original party order (clSlot), not Showdown's switch-reordered order
  return side.pokemon.slice().sort((a, b) => a.clSlot - b.clSlot).map(p => ({
    hp: p.hp, maxhp: p.maxhp, status: p.status === 'fnt' ? '' : p.status || '', fainted: !!p.fainted, active: !!p.isActive,
    pp: p.baseMoveSlots.map(m => m.pp), maxpp: p.baseMoveSlots.map(m => m.maxpp),
  }));
}

function startBattle(sock, init) {
  const send = o => sock.writable && sock.write(JSON.stringify(o) + '\n');
  const stream = new BattleStream();
  const streams = getPlayerStreams(stream);
  if (init.skill != null) new TrainerAI(streams.p2, () => stream.battle, init.skill).start();
  else if (!init.pvp) new RandomPlayerAI(streams.p2).start();

  for (const side of init.pvp ? ['p1', 'p2'] : ['p1']) (async () => {
    for await (const chunk of streams[side]) {
      for (const line of chunk.split('\n')) {
        if (line.startsWith('|request|') && line.length > 9) {
          const request = JSON.parse(line.slice(9));
          // move types, for the type-coloured move tiles
          for (const a of request.active || []) for (const m of a.moves) m.type = Dex.moves.get(m.id).type.toLowerCase();
          // side.pokemon is in Showdown's switch-reordered order; slot = position in the team we sent
          if (request.side) request.side.pokemon.forEach((p, i) => { p.slot = stream.battle[side].pokemon[i].clSlot; });
          send({ type: 'request', request, side });
        }
        else if (line.startsWith('|error|')) send({ type: 'error', message: line.slice(7), side });
      }
    }
  })().catch(e => send({ type: 'error', message: String(e) }));

  (async () => {
    for await (const chunk of streams.spectator) {
      send({ type: 'log', lines: chunk.split('\n') });
      const b = stream.battle;
      if (b) send({ type: 'state', p1: state(b.p1), p2: state(b.p2) });
      if (b && b.ended) {
        send({ type: 'end', winner: b.winner === 'Player' ? 'p1' : ['Wild', 'Rival', 'Trainer'].includes(b.winner) ? 'p2' : null });
        sock.end();
      }
    }
  })().catch(e => send({ type: 'error', message: String(e) }));

  // doubles / triples: Cobblemon's GEN_9_DOUBLES / GEN_9_TRIPLES
  const format = init.format === 'doubles' || init.format === 'triples' ? { ...FORMAT, gameType: init.format } : FORMAT;
  streams.omniscient.write(`>start ${JSON.stringify({ format })}`);
  streams.omniscient.write(`>player p1 ${JSON.stringify({ name: 'Player', team: pack(init.p1) })}`);
  stream.battle.p1.pokemon.forEach((p, i) => { p.clSlot = i; });
  streams.omniscient.write(`>player p2 ${JSON.stringify({ name: init.pvp ? 'Rival' : init.skill != null ? 'Trainer' : 'Wild', team: pack(init.p2) })}`);
  stream.battle.p2.pokemon.forEach((p, i) => { p.clSlot = i; });
  return {
    choose: (c, side) => streams.omniscient.write(`>${init.pvp && side === 'p2' ? 'p2' : 'p1'} ${c}`),
    forfeit: side => streams.omniscient.write(`>forcelose ${side === 'p2' ? 'p2' : 'p1'}`),
    end: () => stream.writeEnd(),
  };
}

// useitem <pokemon uuid> <item name> <bag item id> [data...]: Cobblemon's BagItemActionResponse
const ONE = 'move [1-4]( -?[1-3])?|switch [1-6]|pass|useitem [0-9a-f-]{36} [^\\s|]+ [a-z_]+( [a-z0-9_.]+)*';
const CHOICE = new RegExp(`^(${ONE})(, (${ONE}))*$`); // one choice per active position

net.createServer(sock => {
  let battle = null, buf = '';
  sock.setEncoding('utf8');
  sock.on('data', d => {
    buf += d;
    let i;
    while ((i = buf.indexOf('\n')) >= 0) {
      const line = buf.slice(0, i); buf = buf.slice(i + 1);
      if (!line.trim()) continue;
      try {
        const msg = JSON.parse(line);
        if (!battle) battle = startBattle(sock, msg);
        else if (typeof msg.choose === 'string' && CHOICE.test(msg.choose)) battle.choose(msg.choose, msg.side);
        else if (msg.forfeit) battle.forfeit(msg.forfeit);
      } catch (e) {
        sock.write(JSON.stringify({ type: 'error', message: String(e) }) + '\n');
      }
    }
  });
  const close = () => { if (battle) battle.end(); battle = null; };
  sock.on('close', close);
  sock.on('error', close);
}).listen(PORT, () => console.log(`Cobblemon showdown sim listening on ${PORT}`));
