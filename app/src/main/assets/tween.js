// Numeric tweens advance in the renderer's single animation frame, before drawing.
const running = new Map();
export const animationEngine = {
    advance(now) {
        for (const [target, tween] of running) {
            const progress = Math.min(1, Math.max(0, (now - tween.start) / tween.duration));
            const eased = 1 - Math.pow(1 - progress, 3);
            for (const key of tween.keys) target[key] = tween.from[key] + (tween.to[key] - tween.from[key]) * eased;
            if (progress === 1) running.delete(target);
        }
    },
    get active() { return running.size > 0; },
    to(target, options) {
        const keys = Object.keys(options).filter(key => typeof options[key] === 'number' && key !== 'duration');
        const from = Object.fromEntries(keys.map(key => [key, target[key]]));
        if (matchMedia('(prefers-reduced-motion: reduce)').matches) {
            for (const key of keys) target[key] = options[key];
            running.delete(target);
            return;
        }
        running.set(target, {keys, from, to: options, start: performance.now(), duration: Math.max(1, (options.duration || 0) * 1000)});
    },
    killTweensOf(target) { running.delete(target); }
};
document.addEventListener('visibilitychange', () => {
    if (!document.hidden) return;
    for (const [target, tween] of running) for (const key of tween.keys) target[key] = tween.to[key];
    running.clear();
});
