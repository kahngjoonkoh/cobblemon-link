// Bedrock bot that battles: hits the first Pokémon (modelengine:*) it is shown, then answers battle forms with
// FORM_ANSWERS (comma list of button indexes, repeated last) and logs forms, boss bars and texts.
// usage: node bedbattle.js HOST PORT SECONDS [answers]
const bp = require('bedrock-protocol')
const [host, port, secs] = [process.argv[2], +process.argv[3], +(process.argv[4] || 60)]
const answers = (process.argv[5] || '0,0').split(',').map(Number)
const c = bp.createClient({ host, port, offline: true, username: process.env.NAME || 'PocBedrock', skipPing: true, connectTimeout: 20000, skinData: { LanguageCode: process.env.LANG_CODE || 'en_GB' } }) // LANG_CODE: the Bedrock language, e.g. de_DE
if (process.env.XUID) { if (c.profile) c.profile.xuid = process.env.XUID; else c.once('session', () => { c.profile.xuid = process.env.XUID }) } // XUID: a distinct Floodgate player per bot name
let me, target, n = 0, pos, held = {}
c.on('start_game', p => {
  me = p.runtime_entity_id; pos = p.player_position; console.log('start_game')
  if (process.env.CMD) setTimeout(() => { console.log('command', process.env.CMD); c.queue('command_request', { command: process.env.CMD, origin: { type: 'player', uuid: '00000000-0000-0000-0000-000000000000', request_id: '', player_entity_id: 0n }, internal: false, version: '72' }) }, 6000)
})
c.on('move_player', p => { if (p.runtime_id == me) pos = p.position })
c.on('add_player', p => { if (process.env.TARGET_PLAYER && p.username === process.env.TARGET_PLAYER) c.emit('add_entity', { entity_type: 'player:' + p.username, runtime_id: p.runtime_id }) })
c.on('add_entity', p => {
  console.log('add_entity', p.entity_type, p.runtime_id)
  if (!target && p.entity_type.startsWith(process.env.TARGET || 'modelengine:')) { // TARGET: only use it, no hit
    target = p.runtime_id
    setTimeout(() => {
      if (!process.env.TARGET) console.log('hitting', target)
      if (!process.env.TARGET) c.queue('inventory_transaction', { transaction: { legacy: { legacy_request_id: 0 }, transaction_type: 'item_use_on_entity', actions: [],
        transaction_data: { entity_runtime_id: target, action_type: 'attack', hotbar_slot: 0, held_item: { network_id: 0 },
          player_pos: pos, click_pos: { x: 0, y: 0, z: 0 } } } })
      setTimeout(() => { console.log('using', target); c.queue('inventory_transaction', { transaction: { legacy: { legacy_request_id: 0 }, transaction_type: 'item_use_on_entity', actions: [],
        transaction_data: { entity_runtime_id: target, action_type: 'interact', hotbar_slot: 0, held_item: { network_id: 0 },
          player_pos: pos, click_pos: { x: 0, y: 0.5, z: 0 } } } }) }, 2000)
    }, +(process.env.TAP_DELAY || 3000)) // TAP_DELAY: ms before using the target
  }
})
c.on('container_open', p => console.log('container_open', p.window_id, p.window_type, JSON.stringify(p.coordinates)))
c.on('inventory_content', p => { if (p.window_id !== 'inventory' && p.window_id !== 0) console.log('container_content', p.window_id, (p.input || []).map(it => it.network_id).join(',')) })
c.on('inventory_slot', p => { if (p.window_id === 'inventory' || p.window_id === 0) held[p.slot] = p.item })
c.on('inventory_content', p => { if (p.window_id === 'inventory' || p.window_id === 0) (p.input || []).forEach((it, i) => held[i] = it) })
if (process.env.USE_ITEM) setTimeout(() => {
  console.log('using item in slot 0', held[0] && held[0].network_id)
  c.queue('inventory_transaction', { transaction: { legacy: { legacy_request_id: 0 }, transaction_type: 'item_use', actions: [],
    transaction_data: { action_type: 'click_air', trigger_type: 'player_input', block_position: { x: 0, y: 0, z: 0 }, face: 255, hotbar_slot: 0,
      held_item: held[0], player_pos: pos, click_pos: { x: 0, y: 0, z: 0 }, block_runtime_id: 0, client_prediction: 'success' } } })
}, +process.env.USE_ITEM)
const prefer = (process.env.PREFER || '').split(',').filter(Boolean) // PREFER_ONCE: each label is used once
c.on('modal_form_request', p => {
  const f = JSON.parse(p.data)
  console.log('form', f.title, '|', (typeof f.content === 'string' ? f.content : '').replace(/\n/g, ' / '), '|', (f.buttons || []).map(b => b.text.replace(/\n/g, ' ') + '<' + (b.image && b.image.data) + '>').join(' ; '))
  let a = answers[Math.min(n++, answers.length - 1)]
  // PREFER: button labels in order of preference (the first that a form has wins), e.g. for a trade
  for (const want of prefer) {
    const i = (f.buttons || []).findIndex(b => b.text.includes(want))
    if (i >= 0) { a = i; if (process.env.PREFER_ONCE) prefer.splice(prefer.indexOf(want), 1); break }
  }
  // a custom form (dropdowns, text fields): every element answered with a (a dropdown index, or DROPDOWN env)
  if (f.type === 'custom_form') {
    console.log('custom', JSON.stringify((f.content || []).map(e => e.options || e.text)))
    a = (f.content || []).map(e => e.type === 'dropdown' ? +(process.env.DROPDOWN || a) : e.type === 'input' ? (process.env.TEXT || 'bot') : null)
  }
  // -1: closed, as a player closing the form (no answer)
  setTimeout(() => c.queue('modal_form_response', a === -1 ? { form_id: p.form_id, has_response_data: false, has_cancel_reason: true, cancel_reason: 'closed' }
    : { form_id: p.form_id, has_response_data: true, data: JSON.stringify(a), has_cancel_reason: false }), 1500)
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
// INPUT=1: send movement input every tick like a real client. SNEAK_AT / SNEAK_FOR (ms): hold sneak;
// FORWARD_AT / FORWARD_FOR (ms): hold W (walk, or steer a ride). Positions follow the server's corrections.
if (process.env.INPUT) {
  let tick = 0n, sneaking = false, start = Date.now(), yaw = 0
  c.on('move_player', p => { if (p.runtime_id == me) yaw = p.yaw })
  c.on('spawn', () => {
    setInterval(() => {
      const t = Date.now() - start
      const sneak = process.env.SNEAK_AT && t > +process.env.SNEAK_AT && t < +process.env.SNEAK_AT + +(process.env.SNEAK_FOR || 2000)
      const fwd = process.env.FORWARD_AT && t > +process.env.FORWARD_AT && t < +process.env.FORWARD_AT + +(process.env.FORWARD_FOR || 3000)
      const flags = []
      if (sneak !== sneaking) console.log('sneak', sneak, 'at', t)
      if (sneak && !sneaking) flags.push('start_sneaking')
      if (!sneak && sneaking) flags.push('stop_sneaking')
      if (sneak) flags.push('sneaking', 'sneak_down')
      if (fwd) flags.push('up')
      sneaking = sneak
      const mv = { x: 0, z: fwd ? 1 : 0 }
      try {
        c.queue('player_auth_input', { pitch: 0, yaw, position: pos || { x: 0, y: 0, z: 0 }, move_vector: mv, head_yaw: yaw, input_data: flags,
          input_mode: 'mouse', play_mode: 'normal', interaction_model: 'crosshair', interact_rotation: { x: 0, z: 0 }, tick: tick++,
          delta: { x: 0, y: 0, z: 0 }, analogue_move_vector: mv, camera_orientation: { x: 0, y: 0, z: 1 }, raw_move_vector: mv })
      } catch (e) { console.log('input error', e.message); process.exit(1) }
    }, 50)
  })
}
