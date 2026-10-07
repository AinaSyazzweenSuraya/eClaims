/**
 * deCrack E-Claims — OCR Feature (ocr.js)
 *
 * COMPLETELY SEPARATE from claim-form.js
 * This file only runs when OCR_ENABLED = true
 *
 * What it does:
 * - Adds a "Scan Amount" button next to every paperclip
 * - When clicked: sends receipt to /api/ocr/scan
 * - On success: fills the Amount field of that row automatically
 * - Staff reviews and corrects if needed
 *
 * To disable: set eclaims.features.ocr-enabled=false → this file still
 * loads but does nothing (OCR_ENABLED = false check at top)
 */

document.addEventListener('DOMContentLoaded', () => {
    if (typeof OCR_ENABLED === 'undefined' || !OCR_ENABLED) return;

    // OCR is enabled — add scan buttons to all existing rows
    initOcrButtons();

    // Watch for dynamically added rows (when staff clicks Add Row)
    observeNewRows();
});

// ── Add scan button to all attach cells ──────────────────────
function initOcrButtons() {
    document.querySelectorAll('.btn-attach').forEach(btn => {
        addScanButton(btn.closest('td') || btn.parentElement.closest('td'));
    });
}

// ── Watch for new rows added dynamically ─────────────────────
function observeNewRows() {
    const sections = ['medicalSection','mealSection','travelSection','othersSection'];
    sections.forEach(sectionId => {
        const tbody = document.querySelector(`#${sectionId} tbody`);
        if (!tbody) return;
        const observer = new MutationObserver(() => {
            // New row added — find attach cells without scan button and add one
            tbody.querySelectorAll('td').forEach(td => {
                if (td.querySelector('.btn-attach') && !td.querySelector('.btn-ocr-scan')) {
                    addScanButton(td);
                }
            });
        });
        observer.observe(tbody, { childList: true });
    });
}

// ── Add OCR scan button next to paperclip ─────────────────────
function addScanButton(td) {
    if (!td) return;
    if (td.querySelector('.btn-ocr-scan')) return; // already added

    const btn = document.createElement('button');
    btn.type      = 'button';
    btn.className = 'btn-ocr-scan';
    btn.title     = 'Scan receipt to extract amount';
    btn.innerHTML = '<i class="bi bi-magic"></i>';
    btn.style.cssText = `
        width:28px; height:28px; border-radius:7px;
        border:1px solid #bfdbfe; background:#eff6ff;
        color:#2563eb; cursor:pointer;
        display:inline-flex; align-items:center; justify-content:center;
        font-size:.85rem; margin-left:4px; transition:all .15s;
        vertical-align:middle;
    `;
    btn.onmouseover = () => { btn.style.background='#2563eb'; btn.style.color='#fff'; };
    btn.onmouseout  = () => { btn.style.background='#eff6ff'; btn.style.color='#2563eb'; };

    // Hidden file input specifically for OCR (separate from the normal attach input)
    const ocrInput = document.createElement('input');
    ocrInput.type   = 'file';
    ocrInput.accept = '.jpg,.jpeg,.png,.gif,.pdf';
    ocrInput.style.display = 'none';
    ocrInput.className     = 'ocr-file-input';

    btn.onclick = () => ocrInput.click();
    ocrInput.onchange = (e) => handleOcrScan(e, td);

    td.querySelector('div').appendChild(btn);
    td.querySelector('div').appendChild(ocrInput);
}

// ── Handle OCR scan ────────────────────────────────────────────
async function handleOcrScan(event, td) {
    const file = event.target.files[0];
    if (!file) return;

    const tr          = td.closest('tr');
    const amountInput = tr.querySelector('.row-amount');
    const totalInput  = tr.querySelector('.row-total');
    const scanBtn     = td.querySelector('.btn-ocr-scan');

    if (!amountInput) {
        showOcrToast('Amount field not found for this row', 'error');
        return;
    }

    // Show scanning state
    const origHTML = scanBtn.innerHTML;
    scanBtn.innerHTML   = '<i class="bi bi-arrow-repeat spin"></i>';
    scanBtn.disabled    = true;
    scanBtn.style.background = '#fef9c3';
    scanBtn.style.color      = '#b45309';
    scanBtn.style.borderColor= '#fde68a';

    showOcrToast('Scanning receipt...', 'info');

    try {
        const formData = new FormData();
        formData.append('file', file);

        const res  = await fetch('/api/ocr/scan', {
            method:  'POST',
            headers: csrfHeader(),  // from claim-form.js
            body:    formData
        });
        const data = await res.json();

        if (data.featureDisabled) {
            showOcrToast('OCR feature is not enabled', 'error');
        } else if (data.success && data.amount) {
            // ✅ Fill the amount field
            amountInput.value = data.amount;

            // Sync total field (for manual-entry claim types)
            if (totalInput && !amountInput.readOnly) {
                totalInput.value = data.amount;
            }

            // Update grand total
            if (typeof updateTotals === 'function') updateTotals();

            // Visual feedback on amount field
            amountInput.style.background    = '#f0fdf4';
            amountInput.style.borderColor   = '#86efac';
            amountInput.style.transition    = 'all .3s';
            setTimeout(() => {
                amountInput.style.background  = '';
                amountInput.style.borderColor = '';
            }, 2000);

            showOcrToast(`✓ Amount extracted: RM ${data.amount}`, 'success');
        } else {
            showOcrToast(data.message || 'Could not extract amount', 'error');
        }

    } catch (e) {
        showOcrToast('Scan failed. Please enter amount manually.', 'error');
        console.error('OCR error:', e);
    } finally {
        // Restore button
        scanBtn.innerHTML    = origHTML;
        scanBtn.disabled     = false;
        scanBtn.style.background  = '#eff6ff';
        scanBtn.style.color       = '#2563eb';
        scanBtn.style.borderColor = '#bfdbfe';
        event.target.value = '';
    }
}

// ── OCR-specific toast (uses showToast from claim-form.js if available) ──
function showOcrToast(msg, type) {
    if (typeof showToast === 'function') {
        showToast(msg, type === 'success' ? 'success' : type === 'info' ? 'success' : 'error');
        return;
    }
    // Fallback if showToast not available
    console.log(`[OCR] ${type}: ${msg}`);
}
