// Self-check for the trainer's switching: Charmander facing a Squirtle (water) at skill 5 switches to Bulbasaur (grass).   PORT=9000 node test-switch.js
const net=require('net');const s=net.connect(+process.env.PORT);let b='',done=false;
const mon=(sp,lv,mv,u)=>({name:sp,species:sp,level:lv,moves:mv,uuid:'00000000-0000-0000-0000-00000000000'+u});
s.write(JSON.stringify({skill:5,p1:[mon('squirtle',30,['watergun'],1)],p2:[mon('charmander',10,['scratch'],2),mon('bulbasaur',10,['vinewhip'],3)]})+'\n');
s.on('data',d=>{b+=d;for(let i;(i=b.indexOf('\n'))>=0;b=b.slice(i+1)){const m=JSON.parse(b.slice(0,i));
 if(m.type==='request'&&!m.request.wait)s.write(JSON.stringify({choose:'move 1'})+'\n');
 if(m.type==='log')for(const l of m.lines)if(l.startsWith('|switch|p2a')&&l.includes('Bulbasaur')){console.log('trainer switched to Bulbasaur');process.exit(0);}}});
setTimeout(()=>{console.log('no switch');process.exit(1)},8000);
