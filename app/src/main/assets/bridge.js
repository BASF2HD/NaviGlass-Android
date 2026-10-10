const pending=new Map();let sequence=0;const listeners=new Map();
export function listen(type,callback){listeners.set(type,callback);}
export function deliver(message){if(message.type==='response'){const item=pending.get(message.id);if(!item)return;clearTimeout(item.timer);pending.delete(message.id);message.error?item.reject(new Error(message.error)):item.resolve(message.data);}
 else{listeners.get(message.type)?.(message.data,message.error);}}
export function request(action,data={}){if(!window.nativeBridge)return Promise.reject(new Error('Open the Android APK to use your local music.'));
 const id=String(++sequence);return new Promise((resolve,reject)=>{const timer=setTimeout(()=>{pending.delete(id);reject(new Error('The operation is taking longer than expected. Try again after the scan completes.'));},30000);
 pending.set(id,{resolve,reject,timer});window.nativeBridge.postMessage(JSON.stringify({id,action,...data}));});}
if(window.nativeBridge)window.nativeBridge.onmessage=event=>{try{deliver(JSON.parse(event.data));}catch(error){console.error('Invalid native response');}};
