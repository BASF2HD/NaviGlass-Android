// Synthetic design-preview data. Never used in the native Android player.
import {deliver} from './bridge.js';
const names=['Afterglow','Blue Hour','Quiet Cities','Parallel Lines','Slow Morning','Night Drive','Wildflowers','Open Water','Sunroom','Wavelengths','Daydreams','Northbound'];
function cover(i){const colour=['#c18465','#647dc1','#749c87','#b89457','#9f6b9c','#6996af'][i%6];
 return 'data:image/svg+xml,'+encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512"><rect width="512" height="512" fill="${colour}"/><circle cx="320" cy="180" r="180" fill="#ffffff" opacity=".1"/><path d="M-40 370L260 70L570 380V560H-40Z" fill="#172234" opacity=".5"/><path d="M-40 490L310 210L570 450V560H-40Z" fill="#101828" opacity=".7"/><text x="35" y="410" fill="#eee8de" font-family="sans-serif" font-size="38" font-weight="bold">${names[i]}</text><text x="38" y="445" fill="#d7d7d7" font-family="sans-serif" font-size="16" letter-spacing="4">NAVIGLASS DEMO</text></svg>`);}
const albums=names.map((title,i)=>({id:'demo-'+i,title,artist:'The Glass Ensemble',year:String(2014+i),count:8,coverId:'demo-'+i,demoCover:cover(i)}));
const favourites=new Set(),playlists=[];let state={playing:false,position:0,duration:225000,shuffle:false,repeat:0},queue=[];
function song(album,i){return {id:album.id+'-song-'+i,albumId:album.id,title:['First Light','Somewhere New','Hold the Horizon','Little Things','Into the Blue','Homeward','Golden Days','After Hours'][i],artist:album.artist,album:album.title,year:album.year,number:i+1,duration:225000,favourite:favourites.has(album.id+'-song-'+i)};}
const all=albums.flatMap(a=>Array.from({length:8},(_,i)=>song(a,i)));
function emit(){if(state.track)state.track.favourite=favourites.has(state.track.id);deliver({type:'state',data:state});}
window.nativeBridge={postMessage(raw){const req=JSON.parse(raw);let data={};
 switch(req.action){case 'ready':deliver({type:'catalog',data:{albums}});emit();break;
 case 'tracks':data.tracks=all.filter(t=>req.mode==='album'?t.albumId===req.value:req.mode==='favourites'?favourites.has(t.id):req.mode==='playlist'?(playlists.find(p=>p.id===req.value)?.tracks||[]).includes(t.id):(t.title+' '+t.album+' '+t.artist).toLowerCase().includes(req.value.toLowerCase())).map(t=>({...t,favourite:favourites.has(t.id)}));break;
 case 'play':queue=req.ids;state.track={...all.find(t=>t.id===queue[req.index])};state.playing=true;state.position=0;emit();break;
 case 'toggle':state.playing=!state.playing;emit();break;
 case 'next':case 'previous':{const i=queue.indexOf(state.track?.id)+(req.action==='next'?1:-1);if(queue[i]){state.track={...all.find(t=>t.id===queue[i])};state.position=0;}emit();break;}
 case 'seek':state.position=req.position;emit();break;case 'shuffle':state.shuffle=!state.shuffle;emit();break;case 'repeat':state.repeat=(state.repeat+1)%3;emit();break;
 case 'favourite':favourites.has(req.track)?favourites.delete(req.track):favourites.add(req.track);data.favourite=favourites.has(req.track);emit();break;
 case 'playlists':data.playlists=playlists.map(p=>({...p,count:p.tracks.length}));break;
 case 'createPlaylist':data.playlist='p'+playlists.length;playlists.push({id:data.playlist,name:req.name,tracks:[]});break;
 case 'addPlaylist':playlists.find(p=>p.id===req.playlist)?.tracks.push(req.track);break;
 default:deliver({type:'error',error:'Design preview only. Source selection works inside the Android app.'});}
 queueMicrotask(()=>deliver({type:'response',id:req.id,data}));}};
setInterval(()=>{if(state.playing){state.position=Math.min(state.duration,state.position+500);emit();}},500);
