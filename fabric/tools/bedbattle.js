// Bedrock bot that battles: hits the first Pokémon (modelengine:*) it is shown, then answers battle forms with
// FORM_ANSWERS (comma list of button indexes, repeated last) and logs forms, boss bars and texts.
// usage: node bedbattle.js HOST PORT SECONDS [answers]
const bp = require('bedrock-protocol')
const [host, port, secs] = [process.argv[2], +process.argv[3], +(process.argv[4] || 60)]
const answers = (process.argv[5] || '0,0').split(',').map(Number)
const c = bp.createClient({ host, port, offline: true, username: 'PocBedrock', skipPing: true, connectTimeout: 20000 })
let me, target, n = 0, pos, held = {}
c.on('start_game', p => {
  me = p.runtime_entity_id; pos = p.player_position; console.log('start_game')
  if (process.env.CMD) setTimeout(() => { console.log('command', process.env.CMD); c.queue('command_request', { command: process.env.CMD, origin: { type: 'player', uuid: '00000000-0000-0000-0000-000000000000', request_id: '', player_entity_id: 0n }, internal: false, version: '72' }) }, 6000)
})
c.on('move_player', p => { if (p.runtime_id == me) pos = p.position })
c.on('add_entity', p => {
  console.log('add_entity', p.entity_type, p.runtime_id)
  if (!target && p.entity_type.startsWith('modelengine:')) {
    target = p.runtime_id
    setTimeout(() => {
      console.log('hitting', target)
      c.queue('inventory_transaction', { transaction: { legacy: { legacy_request_id: 0 }, transaction_type: 'item_use_on_entity', actions: [],
        transaction_data: { entity_runtime_id: target, action_type: 'attack', hotbar_slot: 0, held_item: { network_id: 0 },
          player_pos: pos, click_pos: { x: 0, y: 0, z: 0 } } } })
      setTimeout(() => { console.log('using', target); c.queue('inventory_transaction', { transaction: { legacy: { legacy_request_id: 0 }, transaction_type: 'item_use_on_entity', actions: [],
        transaction_data: { entity_runtime_id: target, action_type: 'interact', hotbar_slot: 0, held_item: { network_id: 0 },
          player_pos: pos, click_pos: { x: 0, y: 0.5, z: 0 } } } }) }, 2000)
    }, 3000)
  }
})
c.on('inventory_slot', p => { if (p.window_id === 'inventory' || p.window_id === 0) held[p.slot] = p.item })
c.on('inventory_content', p => { if (p.window_id === 'inventory' || p.window_id === 0) (p.input || []).forEach((it, i) => held[i] = it) })
if (process.env.USE_ITEM) setTimeout(() => {
  console.log('using item in slot 0', held[0] && held[0].network_id)
  c.queue('inventory_transaction', { transaction: { legacy: { legacy_request_id: 0 }, transaction_type: 'item_use', actions: [],
    transaction_data: { action_type: 'click_air', trigger_type: 'player_input', block_position: { x: 0, y: 0, z: 0 }, face: 255, hotbar_slot: 0,
      held_item: held[0], player_pos: pos, click_pos: { x: 0, y: 0, z: 0 }, block_runtime_id: 0, client_prediction: 'success' } } })
}, +process.env.USE_ITEM)
c.on('modal_form_request', p => {
  const f = JSON.parse(p.data)
  console.log('form', f.title, '|', (f.content || '').replace(/\n/g, ' / '), '|', (f.buttons || []).map(b => b.text.replace(/\n/g, ' ') + '<' + (b.image && b.image.data) + '>').join(' ; '))
  const a = answers[Math.min(n++, answers.length - 1)]
  setTimeout(() => c.queue('modal_form_response', { form_id: p.form_id, has_response_data: true, data: JSON.stringify(a), has_cancel_reason: false }), 1500)
})
c.on('set_entity_data', p => p.properties && (p.properties.ints || []).length && console.log('props', p.runtime_entity_id, JSON.stringify(p.properties.ints)))
c.on('play_sound', p => console.log('sound', p.name))
c.on('level_sound_event', p => p.sound_id && console.log('levelsound', p.sound_id, p.extra_data))
c.on('boss_event', p => p.title && console.log('bossbar', p.title, p.progress))
c.on('text', p => console.log('text', p.type, JSON.stringify(p.message)))
c.on('set_title', p => p.text && console.log('title', p.type, JSON.stringify(p.text)))
c.on('disconnect', p => { console.log('disconnect', p.message); process.exit(0) })
c.on('error', e => { console.log('error', e.message) })
setTimeout(() => { console.log('done after', secs, 's'); process.exit(0) }, secs * 1000)
