// Battle sidecar: one Pokémon Showdown battle per TCP connection, newline-delimited JSON.
//   in : {"p1":[set...],"p2":[set...],"hp1":[hp|null...]}   then {"choose":"move 1"} / {"choose":"switch 2"}
//   out: {"type":"request","request":{...}}  {"type":"log","lines":[...]}
//        {"type":"state","p1":[...],"p2":[...]}  {"type":"end","winner":"p1"|"p2"|null}
// p2 (the wild Pokémon) picks random legal moves. Sets use Showdown's PokemonSet shape.
'use strict';
const net = require('net');
const { BattleStream, Dex, getPlayerStreams, Teams } = require('pokemon-showdown');
const { RandomPlayerAI } = require('pokemon-showdown/dist/sim/tools/random-player-ai');

const PORT = +(process.env.PORT || 9000);
const FORMAT = 'gen9customgame@@@!Team Preview';  // wild battles: no team preview

function state(side) {
  // reported in original party order (clSlot), not Showdown's switch-reordered order
  return side.pokemon.slice().sort((a, b) => a.clSlot - b.clSlot)
    .map(p => ({ hp: p.hp, maxhp: p.maxhp, status: p.status || '', fainted: !!p.fainted, active: !!p.isActive }));
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

  streams.omniscient.write(`>start ${JSON.stringify({ formatid: FORMAT })}`);
  streams.omniscient.write(`>player p1 ${JSON.stringify({ name: 'Player', team: Teams.pack(init.p1) })}`);
  // persisted HP has to be in place before p2 joins, because joining starts the battle
  stream.battle.p1.pokemon.forEach((p, i) => {
    p.clSlot = i;
    const hp = (init.hp1 || [])[i];
    if (hp != null) { p.hp = Math.max(0, Math.min(hp, p.maxhp)); if (p.hp === 0) p.fainted = true; }
  });
  streams.omniscient.write(`>player p2 ${JSON.stringify({ name: 'Wild', team: Teams.pack(init.p2) })}`);
  stream.battle.p2.pokemon.forEach((p, i) => { p.clSlot = i; });
  return { choose: c => streams.omniscient.write(`>p1 ${c}`), end: () => stream.writeEnd() };
}

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
        else if (typeof msg.choose === 'string' && /^(move [1-4]|switch [1-6])$/.test(msg.choose)) battle.choose(msg.choose);
      } catch (e) {
        sock.write(JSON.stringify({ type: 'error', message: String(e) }) + '\n');
      }
    }
  });
  const close = () => { if (battle) battle.end(); battle = null; };
  sock.on('close', close);
  sock.on('error', close);
}).listen(PORT, () => console.log(`showdown sim listening on ${PORT}`));
