// Self-check for doubles (FORMAT=triples for triples): two choices a turn ("move 1 1, move 1 2"), both sides' requests have two active, the battle ends.   PORT=9000 node test-doubles.js
const net=require('net');const s=net.connect(+process.env.PORT);let b='',two=0;
const mon=(sp,u)=>({name:sp,species:sp,level:30,moves:['tackle'],uuid:'00000000-0000-0000-0000-00000000000'+u});
s.write(JSON.stringify({format:process.env.FORMAT||'doubles',skill:3,p1:[mon('pikachu',1),mon('eevee',2),mon('bulbasaur',5)],p2:[mon('rattata',3),mon('pidgey',4),mon('weedle',6)]})+'\n');
s.on('data',d=>{b+=d;for(let i;(i=b.indexOf('\n'))>=0;b=b.slice(i+1)){const m=JSON.parse(b.slice(0,i));
 if(m.type==='request'&&!m.request.wait){if((m.request.active||[]).length>=2)two++;
  const c=m.request.forceSwitch?(u=>m.request.forceSwitch.map(f=>{const j=f?m.request.side.pokemon.findIndex((p,k)=>!p.active&&!p.condition.endsWith(' fnt')&&!u.has(k)):-1;if(j<0)return 'pass';u.add(j);return 'switch '+(j+1);}).join(', '))(new Set()):m.request.active.map((a,k,all)=>m.request.side.pokemon[k].condition.endsWith(' fnt')?'pass':'move 1 '+(all.length-k)).join(', ');
  s.write(JSON.stringify({choose:c})+'\n');}
 if(m.type==='error'){console.log('err',m.message);}
 if(m.type==='end'){console.log('doubles end',m.winner,'two-active requests',two);process.exit(two?0:1);}}});
let last='';s.on('data',d=>{last=String(d).slice(-600)});setTimeout(()=>{console.log('timeout',two,last);process.exit(1)},10000);
