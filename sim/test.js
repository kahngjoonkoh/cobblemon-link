// Self-check for server.js: plays one battle and asserts HP injection, state and the end message.
//   node server.js &   PORT=9000 node test.js
'use strict';
const net = require('net');
const assert = require('assert');

const mon = (species, level, moves) => ({
  name: species, species, level, moves, ability: '', nature: 'Hardy', gender: '',
  evs: { hp: 0, atk: 0, def: 0, spa: 0, spd: 0, spe: 0 }, ivs: { hp: 31, atk: 31, def: 31, spa: 31, spd: 31, spe: 31 },
});
const sock = net.connect(+(process.env.PORT || 9000), process.env.HOST || '127.0.0.1');
let buf = '', first = null, last = null, end = null, swapped = false;
sock.write(JSON.stringify({
  p1: [mon('pikachu', 30, ['thunderbolt', 'notarealmove']), mon('bulbasaur', 8, ['tackle'])],
  p2: [mon('rattata', 3, ['tackle'])],
  hp1: [7, null],
}) + '\n');
sock.on('data', d => {
  buf += d;
  for (let i; (i = buf.indexOf('\n')) >= 0; buf = buf.slice(i + 1)) {
    const m = JSON.parse(buf.slice(0, i));
    if (m.type === 'request' && !m.request.wait) {
      // turn 1 switches to bulbasaur, so Showdown reorders side.pokemon; slot must still name the sent team position
      if (first && !swapped) {
        swapped = true;
        assert.deepStrictEqual(m.request.side.pokemon.map(p => p.slot), [1, 0], 'slot should follow the switch');
      }
      sock.write(JSON.stringify({ choose: !first ? 'switch 2' : m.request.forceSwitch ? 'switch 2' : 'move 1' }) + '\n');
      first = first || m.request;
    } else if (m.type === 'state') last = m;
    else if (m.type === 'end') end = m;
  }
});
sock.on('close', () => {
  assert.ok(first, 'no request');
  assert.match(first.side.pokemon[0].condition, /^7\//, 'persisted HP not injected');
  assert.strictEqual(first.active[0].moves[0].type, 'electric', 'move type missing');
  assert.ok(end, 'no end message');
  assert.ok(swapped, 'never saw the request after the switch');
  assert.strictEqual(last.p1.length, 2);
  assert.strictEqual(last.p2[0].fainted, end.winner === 'p1');
  console.log('sim ok: winner', end.winner, 'pikachu hp', last.p1[0].hp + '/' + last.p1[0].maxhp);
});
setTimeout(() => { console.error('timeout'); process.exit(1); }, 10000).unref();
