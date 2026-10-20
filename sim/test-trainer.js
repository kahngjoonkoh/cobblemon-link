// Self-check for NPC trainers: at skill 5 the trainer always picks its strongest move (Gust on Bulbasaur, not Tackle).   PORT=9000 node test-trainer.js
const net=require('net');const s=net.connect(+process.env.PORT);let b='',used=[];
const mon=(sp,lv,mv,u)=>({name:sp,species:sp,level:lv,moves:mv,uuid:'00000000-0000-0000-0000-00000000000'+u});
s.write(JSON.stringify({skill:5,p1:[mon('bulbasaur',50,['growl'],1)],p2:[mon('pidgey',5,['tackle','gust'],2),mon('rattata',5,['tackle'],3)]})+'\n');
s.on('data',d=>{b+=d;for(let i;(i=b.indexOf('\n'))>=0;b=b.slice(i+1)){const m=JSON.parse(b.slice(0,i));
 if(m.type==='request'&&!m.request.wait)s.write(JSON.stringify({choose:'move 1'})+'\n');
 if(m.type==='log')for(const l of m.lines){const p=l.split('|');if(p[1]==='move'&&p[2].startsWith('p2'))used.push(p[3]);}
 if(used.length>=3){const ok=used.every(x=>x==='Gust');console.log('trainer moves',used);process.exit(ok?0:1);}}});
setTimeout(()=>{console.log('timeout',used);process.exit(1)},8000);
