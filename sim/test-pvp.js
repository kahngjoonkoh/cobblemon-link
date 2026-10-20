// Self-check for pvp battles: both sides get requests and the battle ends.   PORT=9000 node test-pvp.js
const net=require('net');const s=net.connect(+process.env.PORT);let b='',ends=0;const sides={p1:0,p2:0};
const mon=(sp,u)=>({name:sp,species:sp,level:20,moves:['tackle'],uuid:u});
s.write(JSON.stringify({pvp:true,p1:[mon('pikachu','00000000-0000-0000-0000-000000000001')],p2:[mon('eevee','00000000-0000-0000-0000-000000000002')]})+'\n');
s.on('data',d=>{b+=d;for(let i;(i=b.indexOf('\n'))>=0;b=b.slice(i+1)){const m=JSON.parse(b.slice(0,i));
 if(m.type==='request'&&!m.request.wait){sides[m.side]++;s.write(JSON.stringify({choose:'move 1',side:m.side})+'\n');}
 if(m.type==='end'){console.log('pvp end',m.winner,sides);process.exit(sides.p1&&sides.p2?0:1);}}});
setTimeout(()=>{console.log('timeout',sides);process.exit(1)},8000);
