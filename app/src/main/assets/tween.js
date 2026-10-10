// Small demand-driven numeric tweens for the Cover Flow transforms.
const running=new Map();let frame=0;
function tick(now){frame=0;for(const [target,tween] of running){const progress=Math.min(1,Math.max(0,(now-tween.start)/tween.duration));const eased=1-Math.pow(1-progress,3);
    for(const key of tween.keys)target[key]=tween.from[key]+(tween.to[key]-tween.from[key])*eased;
    if(progress===1)running.delete(target);
}if(running.size)frame=requestAnimationFrame(tick);}
export const animationEngine={
    to(target,options){const keys=Object.keys(options).filter(key=>typeof options[key]==='number'&&key!=='duration');
        const from=Object.fromEntries(keys.map(key=>[key,target[key]]));
        if(matchMedia('(prefers-reduced-motion: reduce)').matches){for(const key of keys)target[key]=options[key];running.delete(target);return;}
        running.set(target,{keys,from,to:options,start:performance.now(),duration:Math.max(1,(options.duration||0)*1000)});
        if(!frame)frame=requestAnimationFrame(tick);
    },
    killTweensOf(target){running.delete(target);if(!running.size){cancelAnimationFrame(frame);frame=0;}}
};
document.addEventListener('visibilitychange',()=>{if(document.hidden){cancelAnimationFrame(frame);frame=0;for(const [target,tween] of running)for(const key of tween.keys)target[key]=tween.to[key];running.clear();}});
