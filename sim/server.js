// Battle sidecar: one battle per TCP connection, newline-delimited JSON. Runs Cobblemon's own Showdown build
// (data/cobblemon/showdown.zip, unpacked by tools/build.sh into assets/showdown and mounted at $SHOWDOWN), so HP,
// status and PP carry in and out of battle and bag items work exactly like Cobblemon's.
//   in : {"p1":[set...],"p2":[set...]}   then {"choose":"move 1" | "switch 2" | "pass" | "useitem <uuid> <name> <item> [data...]"}
//   out: {"type":"request","request":{...}}  {"type":"log","lines":[...]}
//        {"type":"state","p1":[...],"p2":[...]}  {"type":"end","winner":"p1"|"p2"|null}
// A set is Showdown's PokemonSet plus Cobblemon's fields: uuid, hp (null = full), status, pp ([current...] or null),
// happiness, ball. p2 (the wild Pokémon) picks random legal moves.
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
  new RandomPlayerAI(streams.p2).start();

  (async () => {
    for await (const chunk of streams.p1) {
      for (const line of chunk.split('\n')) {
        if (line.startsWith('|request|') && line.length > 9) {
          const request = JSON.parse(line.slice(9));
          // move types, for the type-coloured move tiles
          for (const a of request.active || []) for (const m of a.moves) m.type = Dex.moves.get(m.id).type.toLowerCase();
          // side.pokemon is in Showdown's switch-reordered order; slot = position in the team we sent
          if (request.side) request.side.pokemon.forEach((p, i) => { p.slot = stream.battle.p1.pokemon[i].clSlot; });
          send({ type: 'request', request });
        }
        else if (line.startsWith('|error|')) send({ type: 'error', message: line.slice(7) });
      }
    }
  })().catch(e => send({ type: 'error', message: String(e) }));

  (async () => {
    for await (const chunk of streams.spectator) {
      send({ type: 'log', lines: chunk.split('\n') });
      const b = stream.battle;
      if (b) send({ type: 'state', p1: state(b.p1), p2: state(b.p2) });
      if (b && b.ended) {
        send({ type: 'end', winner: b.winner === 'Player' ? 'p1' : b.winner === 'Wild' ? 'p2' : null });
        sock.end();
      }
    }
  })().catch(e => send({ type: 'error', message: String(e) }));

  streams.omniscient.write(`>start ${JSON.stringify({ format: FORMAT })}`);
  streams.omniscient.write(`>player p1 ${JSON.stringify({ name: 'Player', team: pack(init.p1) })}`);
  stream.battle.p1.pokemon.forEach((p, i) => { p.clSlot = i; });
  streams.omniscient.write(`>player p2 ${JSON.stringify({ name: 'Wild', team: pack(init.p2) })}`);
  stream.battle.p2.pokemon.forEach((p, i) => { p.clSlot = i; });
  return { choose: c => streams.omniscient.write(`>p1 ${c}`), end: () => stream.writeEnd() };
}

// useitem <pokemon uuid> <item name> <bag item id> [data...]: Cobblemon's BagItemActionResponse
const CHOICE = /^(move [1-4]|switch [1-6]|pass|useitem [0-9a-f-]{36} [^\s|]+ [a-z_]+( [a-z0-9_.]+)*)$/;

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
        else if (typeof msg.choose === 'string' && CHOICE.test(msg.choose)) battle.choose(msg.choose);
      } catch (e) {
        sock.write(JSON.stringify({ type: 'error', message: String(e) }) + '\n');
      }
    }
  });
  const close = () => { if (battle) battle.end(); battle = null; };
  sock.on('close', close);
  sock.on('error', close);
}).listen(PORT, () => console.log(`Cobblemon showdown sim listening on ${PORT}`));
