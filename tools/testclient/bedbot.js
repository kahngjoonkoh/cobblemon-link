// Bedrock protocol bot: join, report how far it gets and which entities it is sent. usage: node bedbot.js HOST PORT SECONDS
const bp = require('bedrock-protocol')
const [host, port, secs] = [process.argv[2], +process.argv[3], +(process.argv[4] || 40)]
const c = bp.createClient({ host, port, offline: true, username: 'BotBedrock', skipPing: true, connectTimeout: 20000 })
const seen = {}
c.on('packet', p => { const n = p.data.name; seen[n] = (seen[n] || 0) + 1 })
c.on('start_game', p => console.log('start_game: in the world at', JSON.stringify(p.player_position)))
c.on('add_entity', p => console.log('add_entity', p.entity_type, JSON.stringify(p.position)))
c.on('resource_packs_info', p => console.log('packs offered:', (p.texture_packs || []).length))
c.on('text', p => console.log('text', p.type, JSON.stringify(p.message), JSON.stringify(p.parameters || [])))
c.on('set_title', p => p.text && console.log('title', p.type, JSON.stringify(p.text)))
const names = it => { const n = it && it.extra && it.extra.nbt && it.extra.nbt.nbt; const d = n && n.value && n.value.display; return d ? JSON.stringify(d.value.Name && d.value.Name.value) : null }
c.on('inventory_content', p => (p.input || []).forEach((it, i) => { const n = names(it); if (n) console.log('slot', i, it.network_id, n) }))
c.on('item_registry', p => { const m = (p.itemstates || []).filter(x => x.name.startsWith('cobblemonlink:')); console.log('custom items registered:', m.length, m.slice(0, 2).map(x => x.name + '=' + x.runtime_id).join(' ')) })
c.on('inventory_slot', p => { const n = names(p.item); if (n) console.log('slot', p.slot, p.item && p.item.network_id, n) })
c.on('modal_form_request', p => console.log('form', p.form_id, p.data.slice(0, 400)))
c.on('set_entity_data', p => p.properties && (p.properties.ints || []).length && console.log('props', p.runtime_entity_id, JSON.stringify(p.properties.ints)))
c.on('add_entity', p => p.properties && (p.properties.ints || []).length && console.log('props@spawn', p.runtime_id, JSON.stringify(p.properties.ints)))
c.on('disconnect', p => { console.log('disconnect', p.message); process.exit(0) })
c.on('error', e => { console.log('error', e.message); process.exit(1) })
setTimeout(() => { console.log('still connected after', secs, 's; packets:', JSON.stringify(seen).slice(0, 600)); process.exit(0) }, secs * 1000)
