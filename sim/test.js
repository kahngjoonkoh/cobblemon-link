// Self-check for server.js: plays one battle and asserts HP/status/PP injection, a Potion, state and the end message.
//   SHOWDOWN=../assets/showdown node server.js &   PORT=9000 node test.js
'use strict';
const net = require('net');
const assert = require('assert');

const mon = (species, level, moves, uuid, extra) => ({
  name: species, species, level, moves, ability: '', nature: 'Hardy', gender: '', uuid, ...extra,
  evs: { hp: 0, atk: 0, def: 0, spa: 0, spd: 0, spe: 0 }, ivs: { hp: 31, atk: 31, def: 31, spa: 31, spd: 31, spe: 31 },
});
const sock = net.connect(+(process.env.PORT || 9000), process.env.HOST || '127.0.0.1');
const PIKA = '00000000-0000-0000-0000-000000000001';
let buf = '', first = null, last = null, end = null, swapped = false, healed = false, n = 0;
sock.write(JSON.stringify({
  p1: [mon('pikachu', 30, ['thunderbolt', 'notarealmove'], PIKA, { hp: 7, status: 'par', pp: [3] }),
       mon('bulbasaur', 8, ['tackle'], '00000000-0000-0000-0000-000000000002')],
  p2: [mon('rattata', 3, ['tackle'], '00000000-0000-0000-0000-000000000003')],
}) + '\n');
sock.on('data', d => {
  buf += d;
  for (let i; (i = buf.indexOf('\n')) >= 0; buf = buf.slice(i + 1)) {
    const m = JSON.parse(buf.slice(0, i));
    if (m.type === 'request' && !m.request.wait) {
      // turn 1 switches to bulbasaur, so Showdown reorders side.pokemon; slot must still name the sent team position
      if (n === 2 && !swapped) {
        swapped = true;
        assert.deepStrictEqual(m.request.side.pokemon.map(p => p.slot), [1, 0], 'slot should follow the switch');
      }
      // turn 1: a Potion on Pikachu (Cobblemon's bag item, uses the turn); then switch to Bulbasaur; then fight
      const c = n++ === 0 ? `useitem ${PIKA} Potion potion 20` : !swapped && !first.forceSwitch && n === 2 ? 'switch 2'
        : m.request.forceSwitch ? 'switch 2' : 'move 1';
      sock.write(JSON.stringify({ choose: c }) + '\n');
      first = first || m.request;
    } else if (m.type === 'log' && m.lines.some(l => l.startsWith('|-heal|p1a: ' + PIKA) && l.endsWith('[from] bagitem: potion'))) healed = true;
    else if (m.type === 'state') last = m;
    else if (m.type === 'end') end = m;
  }
});
sock.on('close', () => {
  assert.ok(first, 'no request');
  assert.match(first.side.pokemon[0].condition, /^7\/\d+ par$/, 'persisted HP/status not injected');
  assert.strictEqual(first.active[0].moves[0].pp, 3, 'persisted PP not injected');
  assert.ok(healed, 'the Potion did not heal 20 HP');
  assert.strictEqual(first.active[0].moves[0].type, 'electric', 'move type missing');
  assert.ok(end, 'no end message');
  assert.ok(swapped, 'never saw the request after the switch');
  assert.strictEqual(last.p1.length, 2);
  assert.strictEqual(last.p2[0].fainted, end.winner === 'p1');
  console.log('sim ok: winner', end.winner, 'pikachu hp', last.p1[0].hp + '/' + last.p1[0].maxhp);
});
setTimeout(() => { console.error('timeout'); process.exit(1); }, 10000).unref();
