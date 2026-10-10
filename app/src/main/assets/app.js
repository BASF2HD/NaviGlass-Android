import * as flow from './renderer.js';
import {request,listen} from './bridge.js';
import {icon} from './icons.js';
const $=id=>document.getElementById(id),native=Boolean(window.nativeBridge);
let albums=[],albumIndex=0,tracks=[],state={},lastTrack='',seekHeld=false,panelMode='',textureEpoch=0,toastTimer,searchTimer;
const loaded=new Map();
function time(ms){const seconds=Math.floor(Math.max(0,ms||0)/1000);return Math.floor(seconds/60)+':'+String(seconds%60).padStart(2,'0');}
function button(text,action,className=''){const b=document.createElement('button');b.textContent=text;b.className=className;b.onclick=action;return b;}
function toast(text){$('toast').textContent=text;$('toast').hidden=false;clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('toast').hidden=true,5000);}
async function call(action,data={}){try{return await request(action,data);}catch(e){toast(e.message);return null;}}
function openPanel(title,mode){$('panelTitle').textContent=title;$('panelBody').replaceChildren();panelMode=mode;if(!$('panel').open)$('panel').showModal();}
window.closePanel=()=>{if(!$('panel').open)return false;$('panel').close();panelMode='';return true;};
$('close').onclick=window.closePanel;
$('panel').addEventListener('click',event=>{if(event.target===$('panel'))window.closePanel();});
$('panel').addEventListener('cancel',()=>{panelMode='';});
function coverURL(album){return album.demoCover||'/art/'+encodeURIComponent(album.coverId)+'.jpg';}
async function loadWindow(index){
 const epoch=++textureEpoch,radius=flow.getSideCount()+4,start=Math.max(0,index-radius),end=Math.min(albums.length-1,index+radius);
 const needed=new Set();for(let i=start;i<=end;i++)needed.add(i);
 const queue=[index,...Array.from(needed).filter(i=>i!==index).sort((a,b)=>Math.abs(a-index)-Math.abs(b-index))];
 async function consume(){while(queue.length&&epoch===textureEpoch){const i=queue.shift();if(loaded.has(i))continue;const url=coverURL(albums[i]);const texture=await flow.loadTexture(url);
     if(epoch!==textureEpoch)continue;loaded.set(i,{url,texture});flow.setTextureAtIndex(i,texture);}}
 await Promise.all([consume(),consume(),consume()]);
 setTimeout(()=>{if(epoch!==textureEpoch)return;for(const i of loaded.keys())if(!needed.has(i)){flow.setTextureAtIndex(i,flow.getDefaultTexture());loaded.delete(i);}
     flow.retainTextures(new Set(Array.from(loaded.values(),entry=>entry.url)));},550);
}
function selectAlbum(index,animate=true){if(!albums.length)return;albumIndex=Math.max(0,Math.min(albums.length-1,index));animate?flow.navigateTo(albumIndex):flow.jumpTo(albumIndex);caption();loadWindow(albumIndex);}
function caption(){const album=albums[albumIndex];if(!album)return;const playing=state.track?.albumId===album.id;
 $('title').textContent=playing?state.track.title:album.title;
 $('subtitle').textContent=playing?[album.title,album.year].filter(Boolean).join(' | '):[album.artist,album.year].filter(Boolean).join(' | ');
 $('albumPosition').textContent=(albumIndex+1)+' / '+albums.length;
 const bounds=flow.getActiveCoverBounds();if(bounds){$('caption').style.width=Math.min(bounds.width,$('scene').clientWidth-32)+'px';$('caption').style.top=(bounds.bottom+8)+'px';}}
flow.initScene($('scene'));flow.onSnap(caption);window.addEventListener('resize',()=>setTimeout(caption,180));
listen('catalog',data=>{const previous=albums[albumIndex]?.id;albums=data.albums||[];loaded.clear();textureEpoch++;flow.setAlbumData(albums.map(()=>flow.getDefaultTexture()));
 $('welcome').hidden=albums.length>0;$('songs').disabled=!albums.length;$('caption').hidden=!albums.length;
 $('backAlbum').disabled=!albums.length;$('nextAlbum').disabled=!albums.length;
 if(albums.length)selectAlbum(Math.max(0,albums.findIndex(a=>a.id===previous)),false);else $('albumPosition').textContent='';});
listen('error',(_,error)=>toast(error||'Operation failed'));
listen('progress',(data,error)=>{$('scan').hidden=Boolean(data.done);$('scanText').textContent=data.label+' · '+data.indexed.toLocaleString()+' songs · '+data.checked.toLocaleString()+' checked'+(data.skipped?' · '+data.skipped+' skipped':'');
 if(data.done)toast(error||'Library ready: '+data.indexed.toLocaleString()+' songs indexed'+(data.skipped?' ('+data.skipped+' skipped)':''));});
listen('state',data=>{state=data;const t=data.track;icon($('play'),data.playing?'pause':'play',!data.playing);$('play').setAttribute('aria-label',data.playing?'Pause':'Play');
 $('nowTitle').textContent=t?.title||'Nothing playing';$('nowArtist').textContent=t?[t.artist,t.album].filter(Boolean).join(' · '):'Choose an album to begin';
 $('elapsed').textContent=time(data.position);$('duration').textContent=time(data.duration);
 if(!seekHeld)$('seek').value=data.duration?Math.round(data.position/data.duration*1000):0;
 $('seek').disabled=!data.duration;for(const name of ['play','next','previous','shuffle','repeat','nowFavourite'])$(name).disabled=!t;
 $('shuffle').classList.toggle('active',Boolean(data.shuffle));$('repeat').classList.toggle('active',Boolean(data.repeat));$('repeat').dataset.mode=data.repeat===1?'1':data.repeat===2?'all':'';
 $('nowFavourite').classList.toggle('active',Boolean(t?.favourite));icon($('nowFavourite'),'heart',Boolean(t?.favourite));
 document.querySelectorAll('.track').forEach(row=>{const active=row.dataset.id===t?.id;row.classList.toggle('active',active);row.querySelector('.trackNr').textContent=active?'▶':row.dataset.nr;});
 if(t&&t.id!==lastTrack){lastTrack=t.id;const index=albums.findIndex(album=>album.id===t.albumId);if(index>=0)selectAlbum(index);if(data.error)toast(data.error);}caption();});
async function showTracks(mode='album',value=albums[albumIndex]?.id||'',title=albums[albumIndex]?.title||'Songs'){
 openPanel(title,mode);const data=await call('tracks',{mode,value});if(panelMode!==mode||!data)return;tracks=data.tracks;drawTracks();}
function drawTracks(){const body=$('panelBody');body.replaceChildren();if(!tracks.length){const p=document.createElement('p');p.className='hint';p.textContent='No songs here yet.';body.append(p);return;}
 body.append(button('Play all',()=>playTrack(0),'primary'));tracks.forEach((t,index)=>{
 const row=document.createElement('div');row.className='track';row.dataset.id=t.id;row.dataset.nr=t.number||index+1;row.classList.toggle('active',state.track?.id===t.id);
 const play=button('',()=>playTrack(index),'trackPlay');const number=document.createElement('span');number.className='trackNr';number.textContent=state.track?.id===t.id?'▶':row.dataset.nr;
 const label=document.createElement('span');label.className='trackLabel';const title=document.createElement('strong');title.textContent=t.title;const artist=document.createElement('small');artist.textContent=t.artist+' · '+time(t.duration);label.append(title,artist);play.append(number,label);
 const fav=button('',async()=>{const result=await call('favourite',{track:t.id});if(result){t.favourite=result.favourite;icon(fav,'heart',t.favourite);fav.classList.toggle('active',t.favourite);}},'favourite');icon(fav,'heart',t.favourite);fav.classList.toggle('active',t.favourite);fav.setAttribute('aria-label','Favourite '+t.title);
 const add=button('+',()=>addPlaylist(t),'add');add.setAttribute('aria-label','Add '+t.title+' to playlist');row.append(play,fav,add);body.append(row);
 });}
async function playTrack(index){const result=await call('play',{ids:tracks.map(t=>t.id),index});if(result)window.closePanel();}
async function playlists(addTrack){openPanel(addTrack?'Add to playlist':'Playlists','playlists');const result=await call('playlists');if(!result||panelMode!=='playlists')return;
 const body=$('panelBody');body.append(button('＋ New playlist',()=>newPlaylist(addTrack),'primary'));
 for(const p of result.playlists)body.append(button(p.name+' · '+p.count+' songs',async()=>{if(addTrack){const saved=await call('addPlaylist',{playlist:p.id,track:addTrack.id});if(saved){toast('Added to '+p.name);window.closePanel();}}else showTracks('playlist',p.id,p.name);},'playlistItem'));
 if(!result.playlists.length){const hint=document.createElement('p');hint.className='hint';hint.textContent='Create a playlist and add songs using the + button.';body.append(hint);}}
function addPlaylist(t){playlists(t);}
function newPlaylist(track){openPanel('New playlist','create');const input=document.createElement('input');input.type='text';input.maxLength=100;input.placeholder='Playlist name';input.setAttribute('aria-label','Playlist name');
 $('panelBody').append(input,button('Create',async()=>{const result=await call('createPlaylist',{name:input.value});if(result){if(track)await call('addPlaylist',{playlist:result.playlist,track:track.id});playlists();}},'primary'));input.focus();}
function library(){openPanel('Your library','library');const body=$('panelBody'),sources=document.createElement('div');sources.className='sourceButtons';
 sources.append(button('Device music',()=>call('device')),button('Choose folder',()=>call('folder')),button('Refresh source',()=>call('rescan')),button('Favourites',()=>showTracks('favourites','','Favourites')),button('Playlists',()=>playlists()));body.append(sources);
 const hint=document.createElement('p');hint.className='hint';hint.textContent=albums.length.toLocaleString()+' albums · '+albums.reduce((total,a)=>total+a.count,0).toLocaleString()+' songs. Folder access is read-only. Refresh reuses unchanged folder metadata.';body.append(hint);
 const grid=document.createElement('div');grid.className='albumGrid';albums.slice(0,120).forEach((a,i)=>{const b=button('',()=>{selectAlbum(i);window.closePanel();},'albumCard'),image=document.createElement('img'),text=document.createElement('span');image.src=coverURL(a);image.loading='lazy';image.alt='';image.onerror=()=>{image.onerror=null;image.src='icon.svg';};text.textContent=a.title;b.append(image,text);grid.append(b);});body.append(grid);
 if(albums.length>120){const p=document.createElement('p');p.className='hint';p.textContent='Showing the first 120 covers here. Browse all albums in Cover Flow or search any song.';body.append(p);}}
function search(){openPanel('Search music','search');const input=document.createElement('input');input.type='search';input.placeholder='Song, artist or album';input.setAttribute('aria-label','Search music');const results=document.createElement('div');$('panelBody').append(input,results);
 input.oninput=()=>{clearTimeout(searchTimer);const term=input.value;searchTimer=setTimeout(async()=>{const data=await call('tracks',{mode:'search',value:term});if(panelMode!=='search'||input.value!==term||!data)return;
     tracks=data.tracks;const held=input;drawTracks();$('panelBody').prepend(held);held.focus();},220);};input.focus();}
$('search').onclick=search;$('library').onclick=library;$('songs').onclick=()=>showTracks();$('device').onclick=()=>call('device');$('folder').onclick=()=>call('folder');$('cancel').onclick=()=>call('cancel');
$('backAlbum').onclick=()=>selectAlbum(albumIndex-1);$('nextAlbum').onclick=()=>selectAlbum(albumIndex+1);
for(const [id,action] of [['play','toggle'],['next','next'],['previous','previous'],['shuffle','shuffle'],['repeat','repeat']])$(id).onclick=()=>call(action);
$('nowFavourite').onclick=()=>state.track&&call('favourite',{track:state.track.id});
$('seek').onpointerdown=()=>seekHeld=true;$('seek').oninput=()=>{seekHeld=true;$('elapsed').textContent=time(Number($('seek').value)/1000*state.duration);};
$('seek').onchange=()=>{const value=Number($('seek').value)/1000*state.duration;seekHeld=false;call('seek',{position:Math.round(value)});};$('seek').onpointercancel=()=>seekHeld=false;
let pointer;const scene=$('scene');scene.addEventListener('pointerdown',e=>{pointer={x:e.clientX,y:e.clientY};scene.setPointerCapture(e.pointerId);});
scene.addEventListener('pointerup',e=>{if(!pointer)return;const dx=e.clientX-pointer.x,dy=e.clientY-pointer.y;pointer=null;if(Math.abs(dx)>35&&Math.abs(dx)>Math.abs(dy))selectAlbum(albumIndex+(dx<0?1:-1));
 else if(Math.abs(dx)<12&&Math.abs(dy)<12&&albums.length){const box=flow.getActiveCoverBounds();if(box&&e.clientX<box.left)selectAlbum(albumIndex-1);else if(box&&e.clientX>box.left+box.width)selectAlbum(albumIndex+1);else showTracks();}});
scene.addEventListener('pointercancel',()=>pointer=null);scene.onkeydown=e=>{if(e.key==='ArrowRight')selectAlbum(albumIndex+1);if(e.key==='ArrowLeft')selectAlbum(albumIndex-1);if(e.key==='Enter')showTracks();};
if(!native){document.body.classList.add('demo');const p=document.createElement('p');p.className='hint';p.textContent='Browser design preview only. Local music playback requires the Android app.';$('welcome').append(p,button('Explore demo covers',async()=>{await import('./demo.js');call('ready');},'primary'));}
else call('ready');
for(const [id,name] of [['play','play'],['previous','previous'],['next','next'],['shuffle','shuffle'],['repeat','repeat'],['nowFavourite','heart'],['search','search'],['library','menu'],['close','close'],['backAlbum','left'],['nextAlbum','right']])icon($(id),name,id==='play');
for(const name of ['play','next','previous','shuffle','repeat','nowFavourite','seek'])$(name).disabled=!state.track;
window.naviglassDebug={get albums(){return albums.length;},get selectedAlbum(){return albumIndex;},selectAlbum,render:flow.debugRenderState};
