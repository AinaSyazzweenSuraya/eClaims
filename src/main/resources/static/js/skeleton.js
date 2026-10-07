/**
 * deCrack E-Claims — Loading Skeleton (skeleton.js)
 *
 * COMPLETELY SEPARATE from claim-form.js and all other JS files.
 * Zero impact on existing functionality.
 *
 * How it works:
 * 1. On page load — immediately show skeleton placeholder
 * 2. When DOM is ready — fade out skeleton, fade in real content
 */

(function () {
    'use strict';

    // ── Detect which page we're on ────────────────────────────
    function getPageType() {
        const path = window.location.pathname;
        if (path === '/dashboard' || path === '/')          return 'dashboard';
        if (path.includes('/claims/') && path.includes('/edit')) return 'form';
        if (path.includes('/claims/new'))                   return 'form';
        if (path.includes('/claims/'))                      return 'view';
        if (path.includes('/approval/pm'))                  return 'approval';
        if (path.includes('/approval/superior'))            return 'approval';
        if (path.includes('/finance'))                      return 'approval';
        if (path.includes('/admin'))                        return 'admin';
        return 'generic';
    }

    // ── Build skeleton HTML per page type ─────────────────────
    function buildSkeleton(type) {
        const header = `
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:1.5rem">
                <div>
                    <div class="skeleton sk-line-lg" style="width:220px"></div>
                    <div class="skeleton sk-line-sm" style="width:140px"></div>
                </div>
                <div class="skeleton" style="width:120px;height:36px;border-radius:9px"></div>
            </div>`;

        const statsRow = `
            <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:1rem;margin-bottom:1.5rem">
                ${[1,2,3,4].map(() => `
                <div class="sk-card" style="padding:1.25rem">
                    <div class="skeleton sk-line-sm" style="width:60%"></div>
                    <div class="skeleton sk-line-lg" style="width:40%;margin-top:.5rem"></div>
                </div>`).join('')}
            </div>`;

        const tableCard = `
            <div class="sk-card">
                <div class="skeleton sk-line" style="width:180px;margin-bottom:1.25rem"></div>
                ${[1,2,3,4,5].map(i => `
                <div style="display:flex;gap:1rem;margin-bottom:.85rem;align-items:center">
                    <div class="skeleton sk-line" style="width:90px;flex-shrink:0"></div>
                    <div class="skeleton sk-line" style="width:${60 + i * 8}px;flex-shrink:0"></div>
                    <div class="skeleton sk-line" style="flex:1"></div>
                    <div class="skeleton sk-line" style="width:70px;flex-shrink:0"></div>
                    <div class="skeleton sk-line" style="width:55px;flex-shrink:0"></div>
                </div>`).join('')}
            </div>`;

        const formCard = `
            <div class="sk-card">
                <div class="skeleton sk-line" style="width:200px;margin-bottom:1.25rem"></div>
                <div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:1rem;margin-bottom:1rem">
                    ${[1,2,3,4,5,6].map(() => `
                    <div>
                        <div class="skeleton sk-line-sm" style="width:70%;margin-bottom:6px"></div>
                        <div class="skeleton" style="height:36px;border-radius:8px"></div>
                    </div>`).join('')}
                </div>
                <div style="display:flex;gap:.75rem;margin-top:1rem">
                    <div class="skeleton" style="width:120px;height:36px;border-radius:9px"></div>
                    <div class="skeleton" style="width:100px;height:36px;border-radius:9px"></div>
                </div>
            </div>`;

        const infoCard = `
            <div class="sk-card">
                <div style="display:flex;gap:1.5rem">
                    <div>
                        <div class="skeleton sk-line-sm" style="width:60px;margin-bottom:4px"></div>
                        <div class="skeleton sk-line" style="width:120px"></div>
                        <div class="skeleton sk-line-sm" style="width:80px;margin-top:8px;margin-bottom:4px"></div>
                        <div class="skeleton sk-line" style="width:100px"></div>
                        <div class="skeleton sk-line-sm" style="width:70px;margin-top:8px;margin-bottom:4px"></div>
                        <div class="skeleton sk-line" style="width:140px"></div>
                    </div>
                    <div style="flex:1">
                        <div class="skeleton sk-line-sm" style="width:80px;margin-bottom:4px"></div>
                        ${[1,2,3].map(() => `
                        <div style="display:flex;gap:.75rem;margin-bottom:.65rem">
                            <div class="skeleton sk-line" style="width:60px;flex-shrink:0"></div>
                            <div class="skeleton sk-line" style="flex:1"></div>
                        </div>`).join('')}
                    </div>
                </div>
            </div>`;

        switch (type) {
            case 'dashboard':
                return header + statsRow + tableCard;
            case 'view':
                return header + infoCard + tableCard;
            case 'form':
                return header + formCard + formCard;
            case 'approval':
            case 'admin':
                return header + tableCard;
            default:
                return header + tableCard;
        }
    }

    // ── Inject skeleton and hide real content ─────────────────
function showSkeleton() {
    if (!document.body) {
        // Try again after a tiny delay
        return setTimeout(showSkeleton, 10);
    }

    const type = getPageType();
    const skeleton = document.createElement('div');
    skeleton.id = 'skeletonScreen';
    skeleton.className = 'skeleton-screen';
    skeleton.innerHTML = buildSkeleton(type);
    skeleton.style.cssText = `
        position: fixed;
        top: 0; left: 0; right: 0; bottom: 0;
        z-index: 9997;
        overflow: hidden;
        pointer-events: none;
    `;
    document.body.appendChild(skeleton);

    const main = document.querySelector('.main-content');
    if (main) {
        main.style.opacity = '0';
        main.style.transition = 'opacity .3s ease';
    }
}

    // ── Remove skeleton and show real content ─────────────────
    function hideSkeleton() {
        const skeleton = document.getElementById('skeletonScreen');
        if (skeleton) {
            skeleton.style.transition = 'opacity .3s ease';
            skeleton.style.opacity = '0';
            setTimeout(() => skeleton.remove(), 350);
        }
        const main = document.querySelector('.main-content');
        if (main) {
            main.style.opacity = '1';
        }
    }

    // ── Run ───────────────────────────────────────────────────
    // Show skeleton immediately (before DOM ready)
    // Skip for login page — no sidebar there
    if (!window.location.pathname.includes('/login')) {
        showSkeleton();
    }

    // Hide skeleton once DOM is fully loaded
    document.addEventListener('DOMContentLoaded', function () {
        // Small delay so the transition looks smooth
        setTimeout(hideSkeleton, 400);
    });

})();
