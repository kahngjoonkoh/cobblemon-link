// Bedrock protocol bot for custom blocks: joins, lists the custom blocks Geyser registers (start_game) and the packs it
// is offered, then reports each block update it's sent at the positions given as runtime id -> block name.
// usage: node bedblocks.js HOST PORT SECONDS [x,y,z ...]
const bp = require('bedrock-protocol')
const [host, port, secs] = [process.argv[2], +process.argv[3], +(process.argv[4] || 40)]
const watch = new Set(process.argv.slice(5))
const c = bp.createClient({ host, port, offline: true, username: 'BotBlocks', skipPing: true, connectTimeout: 20000 })
let names = []
c.on('resource_packs_info', p => console.log('packs offered:', (p.texture_packs || []).map(t => t.uuid).join(' ')))
c.on('start_game', p => {
  const custom = (p.block_properties || []).map(b => b.name)
  const ours = custom.filter(n => n.includes('cl_') || n.includes('clp_'))
  console.log('custom blocks registered:', custom.length, '- ours:', ours.length, ours.slice(0, 12).join(' '))
  console.log('in the world at', JSON.stringify(p.player_position), 'block_network_ids_are_hashes:', p.block_network_ids_are_hashes)
})
c.on('update_block', p => {
  const k = [p.position.x, p.position.y, p.position.z].join(',')
  if (!watch.size || watch.has(k)) console.log('update_block', k, 'runtime', p.block_runtime_id)
})
c.on('text', p => console.log('text', JSON.stringify(p.message)))
c.on('disconnect', p => { console.log('disconnect', p.message); process.exit(0) })
c.on('error', e => { console.log('error', e.message); process.exit(1) })
setTimeout(() => { console.log('still connected after', secs, 's'); process.exit(0) }, secs * 1000)
