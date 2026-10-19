// Bedrock protocol bot: join, report how far it gets and which entities it is sent. usage: node bedbot.js HOST PORT SECONDS
const bp = require('bedrock-protocol')
const [host, port, secs] = [process.argv[2], +process.argv[3], +(process.argv[4] || 40)]
const c = bp.createClient({ host, port, offline: true, username: 'PocBedrock', skipPing: true, connectTimeout: 20000 })
const seen = {}
c.on('packet', p => { const n = p.data.name; seen[n] = (seen[n] || 0) + 1 })
c.on('start_game', p => console.log('start_game: in the world at', JSON.stringify(p.player_position)))
c.on('add_entity', p => console.log('add_entity', p.entity_type, JSON.stringify(p.position)))
c.on('resource_packs_info', p => console.log('packs offered:', (p.texture_packs || []).length))
c.on('disconnect', p => { console.log('disconnect', p.message); process.exit(0) })
c.on('error', e => { console.log('error', e.message); process.exit(1) })
setTimeout(() => { console.log('still connected after', secs, 's; packets:', JSON.stringify(seen).slice(0, 600)); process.exit(0) }, secs * 1000)
