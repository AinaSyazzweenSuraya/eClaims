/**
 * IFC E-Claims — Claim Form JavaScript (Phase 6)
 * Handles: dynamic rows, auto-calculations, AJAX save/submit, row-level attachments
 */

// ================================================================
// DATA FROM SERVER (injected via Thymeleaf inline JS in form.html)
// ================================================================
// CLAIM_TYPES, PANEL_CLINICS, TRAVEL_LOCATIONS, TRAVEL_MEALS, PROJECT_MANAGERS
// are declared in the form template as const variables.

let currentWorkflowId = null;

// Unsaved changes tracking
let isDirty = false;

// Pending attachments: array of {tr, file} waiting for rowId
const pendingAttachments = [];

// ================================================================
// ROW MANAGEMENT
// ================================================================

function addRow(sectionId) {
    const tbody = document.querySelector(`#${sectionId} tbody`);
    const rowNum = tbody.querySelectorAll('tr').length + 1;
    const row    = document.createElement('tr');
    row.dataset.rowNum = rowNum;
    row.innerHTML      = buildRowHtml(sectionId, rowNum);
    tbody.appendChild(row);
    updateTotals();
}

function deleteRow(btn) {
    const tr = btn.closest('tr');
    const ok = confirm('This row will be removed. Do you want to continue?');
    if (!ok) return;
    tr.remove();
    updateTotals();
}

function buildRowHtml(sectionId, n) {
    const claimSelect = buildClaimTypeOptions(sectionId);
    const pmSelect    = buildPmOptions();

    switch (sectionId) {
        case 'medicalSection':
            return `
            <td><input type="date" class="row-input row-date" onchange="updateTotals()"/></td>
            <td>${claimSelect}</td>
            <td>
                <input type="text" class="row-input row-desc medical-nonclinic" placeholder="Description"/>
                <div class="medical-clinic-cell" style="display:none;margin-top:4px">
                    ${buildPanelClinicOptions()}
                </div>
            </td>
            <td><input type="number" class="row-input row-amount" step="0.01" min="0" placeholder="0.00" oninput="syncMedicalTotal(this)"/></td>
            <td><input type="text" class="row-input row-total" readonly style="background:#f8fafc;font-weight:600"/></td>
            <td>${buildAttachCell()}</td>
            <td><button type="button" class="btn-del-row" onclick="deleteRow(this)"><i class="bi bi-trash3"></i></button></td>`;

        case 'mealSection':
            return `
            <td><input type="date" class="row-input row-date" onchange="updateTotals()"/></td>
            <td>${claimSelect}</td>
            <td><input type="text" class="row-input row-desc" placeholder="Description"/></td>
            <td><input type="time" class="row-input row-time-from" oninput="calcMealRow(this)"/></td>
            <td><input type="time" class="row-input row-time-to"   oninput="calcMealRow(this)"/></td>
            <td>${buildPmOptions()}</td>
            <td><input type="number" class="row-input row-amount" step="0.01" readonly style="background:#f8fafc"/></td>
            <td><input type="text"   class="row-input row-total"  readonly style="background:#f8fafc;font-weight:600"/></td>
            <td>${buildAttachCell()}</td>
            <td><button type="button" class="btn-del-row" onclick="deleteRow(this)"><i class="bi bi-trash3"></i></button></td>`;

        case 'travelSection':
            return `
            <td><input type="date" class="row-input row-date" onchange="updateTotals()"/></td>
            <td>${buildTravelLocationOptions()}</td>
            <td>${buildTravelMealOptions()}</td>
            <td><input type="number" class="row-input row-amount" step="0.01" readonly style="background:#f8fafc"/></td>
            <td><input type="text"   class="row-input row-total"  readonly style="background:#f8fafc;font-weight:600"/></td>
            <td>${buildAttachCell()}</td>
            <td><button type="button" class="btn-del-row" onclick="deleteRow(this)"><i class="bi bi-trash3"></i></button></td>`;

        case 'othersSection':
            return `
            <td><input type="date" class="row-input row-date" onchange="updateTotals()"/></td>
            <td>${claimSelect}</td>
            <td>
                <input type="text" class="row-input row-desc others-desc" placeholder="Description"/>
                <div class="mileage-fields" style="display:none;margin-top:4px;display:none">
                    <select class="row-input row-vehicle" onchange="calcMileageRow(this)" style="margin-bottom:4px">
                        <option value="">Select Vehicle...</option>
                        <option value="Car">Car</option>
                        <option value="Motorcycle">Motorcycle</option>
                    </select>
                    <input type="number" class="row-input row-km" placeholder="Distance (KM)" min="0"
                           oninput="calcMileageRow(this)"/>
                </div>
            </td>
            <td>
                <input type="number" class="row-input row-amount" step="0.01" min="0"
                       placeholder="0.00" oninput="syncOthersTotal(this)"/>
            </td>
            <td><input type="text" class="row-input row-total" readonly style="background:#f8fafc;font-weight:600"/></td>
            <td>${buildAttachCell()}</td>
            <td><button type="button" class="btn-del-row" onclick="deleteRow(this)"><i class="bi bi-trash3"></i></button></td>`;
    }
    return '';
}

function buildClaimTypeOptions(sectionId) {
    const groups = {
        medicalSection: ['CL05','CL06','CL07','CL13','CL14'],
        mealSection:    ['CL01','CL02'],
        othersSection:  ['CL03','CL04','CL08','CL09','CL10','CL11']
    };
    const allowed = groups[sectionId] || [];
    const opts = allowed.map(id => {
        const t = CLAIM_TYPES.find(c => c.claimId === id);
        return t ? `<option value="${t.claimId}">${t.claimTitle || t.claimId}</option>` : '';
    }).join('');
    return `<select class="row-input row-claim-id" onchange="onClaimTypeChange(this)">`
         + `<option value="">Select type...</option>${opts}</select>`;
}

function buildPanelClinicOptions() {
    const opts = PANEL_CLINICS.map(p => `<option value="${p.id}">${p.name}</option>`).join('');
    return `<select class="row-input row-panel-clinic"><option value="">Select clinic...</option>${opts}</select>`;
}

function buildTravelLocationOptions() {
    const opts = TRAVEL_LOCATIONS.map(t => `<option value="${t.id}" data-limit="${t.limit}">${t.location}</option>`).join('');
    return `<select class="row-input row-travel-id" onchange="calcTravelRow(this)">`
         + `<option value="">Select location...</option>${opts}</select>`;
}

function buildTravelMealOptions() {
    const opts = TRAVEL_MEALS.map(m => `<option value="${m.id}" data-pct="${m.pct}">${m.desc}</option>`).join('');
    return `<select class="row-input row-meal-id" onchange="calcTravelRow(this)">`
         + `<option value="">Select meal...</option>${opts}</select>`;
}

function buildPmOptions() {
    const opts = PROJECT_MANAGERS.map(p => `<option value="${p.id}">${p.name}</option>`).join('');
    return `<select class="row-input row-pm"><option value="">No PM</option>${opts}</select>`;
}

function buildAttachCell() {
    return `<div style="position:relative">
        <button type="button" class="btn-attach" title="Attach receipt" onclick="triggerAttach(this)">
            <i class="bi bi-paperclip"></i>
        </button>
        <input type="file" class="row-file-input" style="display:none"
               accept=".jpg,.jpeg,.png,.pdf,.doc,.docx"
               onchange="handleRowAttach(this)"/>
        <input type="hidden" class="row-attach-path" value=""/>
        <input type="hidden" class="row-attach-name" value=""/>
    </div>`;
}

// ================================================================
// CLAIM TYPE CHANGE — show/hide fields
// ================================================================

function onClaimTypeChange(select) {
    const tr          = select.closest('tr');
    const id          = select.value;
    const clinicDiv   = tr.querySelector('.medical-clinic-cell');
    const mileageDiv  = tr.querySelector('.mileage-fields');
    const amountInput = tr.querySelector('.row-amount');
    const descInput   = tr.querySelector('.others-desc');

    if (clinicDiv) clinicDiv.style.display = id === 'CL05' ? 'block' : 'none';
    if (mileageDiv) mileageDiv.style.display = id === 'CL04' ? 'block' : 'none';

    if (amountInput) {
        if (id === 'CL04') {
            amountInput.readOnly = true;
            amountInput.style.background = '#f8fafc';
            amountInput.value = '';
        } else {
            amountInput.readOnly = false;
            amountInput.style.background = '#fff';
        }
    }

    const totalInput = tr.querySelector('.row-total');
    if (totalInput) totalInput.value = '';

    updateTotals();
}

// ================================================================
// CALCULATIONS
// ================================================================
function format2Decimals(input) {
    const value = parseFloat(input.value);
    if (!isNaN(value)) {
        input.value = value.toFixed(2);
    }
}

/*function syncMedicalTotal(input) {
    const tr    = input.closest('tr');
    const total = tr.querySelector('.row-total');
    if (total) total.value = parseFloat(input.value || 0).toFixed(2);
    updateTotals();
}*/
function syncMedicalTotal(input) {
    const tr = input.closest('tr');
    const total = tr.querySelector('.row-total');

    let raw = input.value;

    // ignore empty
    if (!raw) {
        if (total) total.value = '';
        updateTotals();
        return;
    }

    // remove non-digits (safety)
    raw = raw.replace(/[^\d]/g, '');

    // convert cents → RM
    const formatted = (parseInt(raw, 10) / 100).toFixed(2);

    // update input display
    input.value = formatted;

    // keep total same behavior as before
    if (total) {
        total.value = formatted;
    }

    updateTotals();
}

function syncOthersTotal(input) {
    const tr      = input.closest('tr');
    const claimId = tr.querySelector('.row-claim-id')?.value;
    if (claimId === 'CL04') return;
    const total = tr.querySelector('.row-total');
    if (total) total.value = parseFloat(input.value || 0).toFixed(2);
    updateTotals();
}

function calcMealRow(input) {
    const tr   = input.closest('tr');
    const from = tr.querySelector('.row-time-from')?.value;
    const to   = tr.querySelector('.row-time-to')?.value;
    const claimId = tr.querySelector('.row-claim-id')?.value;
    if (!from || !to || !claimId) return;

    fetch(`/claims/api/calc/meal?claimId=${claimId}&timeFrom=${from}&timeTo=${to}`)
        .then(r => r.json()).then(data => {
            const amtInput   = tr.querySelector('.row-amount');
            const totalInput = tr.querySelector('.row-total');
            if (amtInput)   amtInput.value   = data.total;
            if (totalInput) totalInput.value  = data.total;
            updateTotals();
        }).catch(() => {});
}

function calcMileageRow(input) {
    const tr      = input.closest('tr');
    const vehicle = tr.querySelector('.row-vehicle')?.value;
    const km      = parseInt(tr.querySelector('.row-km')?.value) || 0;
    if (!vehicle || !km) return;

    fetch(`/claims/api/calc/mileage?vehicleType=${vehicle}&km=${km}`)
        .then(r => r.json()).then(data => {
            const amtInput   = tr.querySelector('.row-amount');
            const totalInput = tr.querySelector('.row-total');
            if (amtInput)   amtInput.value  = data.total;
            if (totalInput) totalInput.value = data.total;
            updateTotals();
        }).catch(() => {});
}

function calcTravelRow(select) {
    const tr      = select.closest('tr');
    const travelId = tr.querySelector('.row-travel-id')?.value;
    const mealId   = tr.querySelector('.row-meal-id')?.value;
    if (!travelId || !mealId) return;

    fetch(`/claims/api/calc/travel?travelId=${travelId}&mealId=${mealId}`)
        .then(r => r.json()).then(data => {
            const amtInput   = tr.querySelector('.row-amount');
            const totalInput = tr.querySelector('.row-total');
            if (amtInput)   amtInput.value  = data.claimLimit;
            if (totalInput) totalInput.value = data.total;
            updateTotals();
        }).catch(() => {});
}

function updateTotals() {
    const sections = ['medicalSection','mealSection','travelSection','othersSection'];
    let grand = 0;
    sections.forEach(id => {
        const tbody = document.querySelector(`#${id} tbody`);
        if (!tbody) return;
        let sub = 0;
        tbody.querySelectorAll('tr').forEach(tr => {
            const total = parseFloat(tr.querySelector('.row-total')?.value) || 0;
            sub += total;
        });
        const subEl = document.getElementById(id + 'Sub');
        if (subEl) subEl.textContent = sub.toFixed(2);
        grand += sub;
    });
    const grandEl = document.getElementById('grandTotal');
    if (grandEl) grandEl.textContent = grand.toFixed(2);
}

// ================================================================
// ROW ATTACHMENT UPLOAD
// ================================================================

function triggerAttach(btn) {
    btn.parentElement.querySelector('.row-file-input').click();
}

async function handleRowAttach(input) {
    const file = input.files[0];
    if (!file) return;

    const tr        = input.closest('tr');
    const btn       = input.parentElement.querySelector('.btn-attach');
    const nameInput = input.parentElement.querySelector('.row-attach-name');
    const rowId     = tr.dataset.rowId;

    if (rowId && currentWorkflowId) {
        await uploadFileToRow(rowId, file, btn);
    } else {
        const idx = pendingAttachments.findIndex(p => p.tr === tr);
        if (idx >= 0) pendingAttachments.splice(idx, 1);
        pendingAttachments.push({ tr, file });

        btn.classList.add('has-file');
        btn.title = file.name;
        if (nameInput) nameInput.value = file.name;
        showToast(`${file.name} will upload on Save Draft`, 'success');
    }
    input.value = '';
}

async function uploadFileToRow(rowId, file, btn) {
    const formData = new FormData();
    formData.append('file', file);
    try {
        const res  = await fetch(`/claims/row/${rowId}/attachment`, {
            method: 'POST', headers: csrfHeader(), body: formData
        });
        const data = await res.json();
        if (data.success) {
            if (btn) { btn.classList.add('has-file'); btn.title = file.name; }
            showToast(`Attached: ${file.name}`, 'success');
            return true;
        } else {
            showToast(`Upload failed: ${data.message}`, 'error');
            return false;
        }
    } catch (e) {
        showToast('Upload failed: ' + e.message, 'error');
        return false;
    }
}

async function uploadPendingAttachments() {
    if (pendingAttachments.length === 0) return;
    for (let i = pendingAttachments.length - 1; i >= 0; i--) {
        const { tr, file } = pendingAttachments[i];
        const rowId = tr.dataset.rowId;
        if (rowId) {
            const btn = tr.querySelector('.btn-attach');
            const ok  = await uploadFileToRow(rowId, file, btn);
            if (ok) pendingAttachments.splice(i, 1);
        }
    }
}

// ================================================================
// COLLECT FORM DATA
// ================================================================

function collectRows() {
    const rows = [];
    const sections = [
        { id: 'medicalSection', type: 'medical' },
        { id: 'mealSection',    type: 'meal' },
        { id: 'travelSection',  type: 'travel' },
        { id: 'othersSection',  type: 'others' }
    ];

    sections.forEach(({ id }) => {
        const tbody = document.querySelector(`#${id} tbody`);
        if (!tbody) return;
        tbody.querySelectorAll('tr').forEach(tr => {
            const row = {
                rowId:               tr.dataset.rowId || null,
                claimId:             tr.querySelector('.row-claim-id')?.value     || (id === 'travelSection' ? 'CL12' : null),
                date:                tr.querySelector('.row-date')?.value         || null,
                description:         tr.querySelector('.row-desc')?.value         || null,
                projectManagerId:    tr.querySelector('.row-pm')?.value           || null,
                timeFrom:            tr.querySelector('.row-time-from')?.value    || null,
                timeTo:              tr.querySelector('.row-time-to')?.value      || null,
                medicalClinic:       tr.querySelector('.row-panel-clinic')?.value || null,
                mileageVehicleType:  tr.querySelector('.row-vehicle')?.value      || null,
                mileageKm:           (dto => dto === 'CL04' ? (parseInt(tr.querySelector('.row-km')?.value) || null) : null)(tr.querySelector('.row-claim-id')?.value),
                travelId:            tr.querySelector('.row-travel-id')?.value    || null,
                mealId:              tr.querySelector('.row-meal-id')?.value      || null,
                amount:              parseFloat(tr.querySelector('.row-amount')?.value) || null,
                total:               parseFloat(tr.querySelector('.row-total')?.value)  || null,
                attachmentPath:      tr.querySelector('.row-attach-path')?.value  || null,
                attachmentOriginalName: tr.querySelector('.row-attach-name')?.value || null,
            };
            if (row.claimId) rows.push(row);
        });
    });
    return rows;
}

// ================================================================
// SAVE DRAFT
// ================================================================

async function saveDraft() {
    const rows = collectRows();
    if (rows.length === 0) { showToast('Add at least one claim row', 'error'); return; }
    try {
        const res  = await fetch('/claims/save-draft', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', ...csrfHeader() },
            body: JSON.stringify({ rows, workflowId: currentWorkflowId })
        });
        const data = await res.json();
        if (data.success) {
            currentWorkflowId = data.workflowId;
            document.getElementById('workflowIdField').value = data.workflowId;
            document.getElementById('formIdDisplay').textContent = 'Form #' + data.formId;

            await refreshRowIds(data.workflowId);

            if (pendingAttachments.length > 0) {
                showToast('Uploading attachments...', 'success');
                await uploadPendingAttachments();
                showToast('Attachments uploaded successfully', 'success');
            }

            showToast('Draft saved — Form #' + data.formId, 'success');
            isDirty = false; // ← clear dirty flag after successful save
        } else {
            showToast(data.message || 'Save failed', 'error');
        }
    } catch (e) { showToast('Save failed: ' + e.message, 'error'); }
}

// ================================================================
// REFRESH ROW IDS
// ================================================================

async function refreshRowIds(workflowId) {
    try {
        const res  = await fetch(`/claims/api/rows/${workflowId}`, {
            headers: csrfHeader()
        });
        if (!res.ok) return;
        const savedRows = await res.json();

        const sections = ['medicalSection','mealSection','travelSection','othersSection'];
        const claimIdToRowIds = {};
        savedRows.forEach(r => {
            if (!claimIdToRowIds[r.claimId]) claimIdToRowIds[r.claimId] = [];
            claimIdToRowIds[r.claimId].push(r.rowId);
        });
        const usageIdx = {};

        sections.forEach(sectionId => {
            const domRows = Array.from(document.querySelectorAll(`#${sectionId} tbody tr`));
            domRows.forEach(tr => {
                const claimId = tr.querySelector('.row-claim-id')?.value
                             || (sectionId === 'travelSection' ? 'CL12' : null);
                if (!claimId) return;
                if (!usageIdx[claimId]) usageIdx[claimId] = 0;
                const rowIds = claimIdToRowIds[claimId] || [];
                if (rowIds[usageIdx[claimId]] !== undefined) {
                    tr.dataset.rowId = rowIds[usageIdx[claimId]];
                    usageIdx[claimId]++;
                }
            });
        });
    } catch (e) {
        console.warn('Could not refresh row IDs:', e);
    }
}

// ================================================================
// SUBMIT CLAIM
// ================================================================

async function submitClaim() {
    if (!currentWorkflowId) {
        await saveDraft();
        if (!currentWorkflowId) return;
    }
    if (!confirm('Submit this claim for approval? You cannot edit after submitting.')) return;
    try {
        isDirty = false; // ← clear before submit to avoid double warning
        const res  = await fetch(`/claims/${currentWorkflowId}/submit`, {
            method: 'POST', headers: csrfHeader()
        });
        const data = await res.json();
        if (data.success) {
            showToast('Claim submitted successfully!', 'success');
            setTimeout(() => window.location.href = data.redirectUrl || '/dashboard', 1500);
        } else {
            showToast(data.message || 'Submit failed', 'error');
        }
    } catch (e) { showToast('Submit failed: ' + e.message, 'error'); }
}

// ================================================================
// CSRF + TOAST
// ================================================================

function csrfHeader() {
    const meta   = document.querySelector('meta[name="_csrf"]');
    const header = document.querySelector('meta[name="_csrf_header"]');
    if (meta && header) return { [header.content]: meta.content };
    return {};
}

function showToast(msg, type = 'success') {
    const wrap = document.getElementById('toastWrap');
    if (!wrap) return;
    const div = document.createElement('div');
    div.className = `toast-item ${type}`;
    div.innerHTML = `<i class="bi bi-${type === 'success' ? 'check-circle' : 'exclamation-circle'}"></i>${msg}`;
    wrap.appendChild(div);
    setTimeout(() => div.remove(), 3500);
}

// ================================================================
// LOAD EXISTING ROWS (edit mode)
// ================================================================

function loadExistingRows() {
    if (!EXISTING_ROWS || EXISTING_ROWS.length === 0) return;

    EXISTING_ROWS.forEach(row => {
        if (!row.claimId) return;
        const id = row.claimId;

        let sectionId;
        if (['CL05','CL06','CL07','CL13','CL14'].includes(id)) sectionId = 'medicalSection';
        else if (['CL01','CL02'].includes(id))    sectionId = 'mealSection';
        else if (id === 'CL12')                    sectionId = 'travelSection';
        else                                       sectionId = 'othersSection';

        addRow(sectionId);

        const tbody = document.querySelector(`#${sectionId} tbody`);
        const tr    = tbody.lastElementChild;
        if (!tr) return;

        if (row.rowId) tr.dataset.rowId = row.rowId;

        setVal(tr, '.row-date',   row.date ? row.date.substring(0,10) : '');
        setVal(tr, '.row-desc',   row.description || '');
        setVal(tr, '.row-amount', row.amount || '');

        const totalVal = row.total != null ? row.total : (row.amount || '');
        setVal(tr, '.row-total', totalVal);

        const claimSel = tr.querySelector('.row-claim-id');
        if (claimSel) {
            claimSel.value = id;
            onClaimTypeChange(claimSel);
        }

        if (sectionId === 'medicalSection') {
            //setVal(tr, '.row-panel-clinic', row.medicalClinic || '');
			setVal(
				tr,
				'.row-amount',
				row.amount != null ? parseFloat(row.amount).toFixed(2) : ''
			);
            const amtEl = tr.querySelector('.row-amount');
            const totEl = tr.querySelector('.row-total');
            if (amtEl && totEl && amtEl.value && !totEl.value) {
                totEl.value = parseFloat(amtEl.value).toFixed(2);
            }
        }

        if (sectionId === 'mealSection') {
            setVal(tr, '.row-time-from', row.timeFrom || '');
            setVal(tr, '.row-time-to',   row.timeTo   || '');
            setVal(tr, '.row-pm',        row.projectManagerId || '');
            const amtEl = tr.querySelector('.row-amount');
            const totEl = tr.querySelector('.row-total');
            if (amtEl && totEl && amtEl.value && !totEl.value) {
                totEl.value = parseFloat(amtEl.value).toFixed(2);
            }
        }

        if (sectionId === 'travelSection') {
            setVal(tr, '.row-travel-id', row.travelId || '');
            setVal(tr, '.row-meal-id',   row.mealId   || '');
        }

        if (sectionId === 'othersSection') {
            if (id === 'CL04' && row.mileageVehicleType) {
                setVal(tr, '.row-vehicle', row.mileageVehicleType);
                setVal(tr, '.row-km',      row.mileageKm || '');
                const vehicleSel = tr.querySelector('.row-vehicle');
                if (vehicleSel) calcMileageRow(vehicleSel);
            } else {
                setVal(tr, '.row-km', row.mileageKm || '');
                const amtEl = tr.querySelector('.row-amount');
                const totEl = tr.querySelector('.row-total');
                if (amtEl && totEl && amtEl.value) {
                    totEl.value = parseFloat(amtEl.value).toFixed(2);
                }
            }
        }

        const hasAttach = row.attachmentPath || row.attachmentOriginalName || row.hasAttachment;
        if (hasAttach) {
            const btn = tr.querySelector('.btn-attach');
            if (btn) {
                btn.classList.add('has-file');
                btn.title = row.attachmentOriginalName || 'Attachment';
            }
            const pathInput = tr.querySelector('.row-attach-path');
            const nameInput = tr.querySelector('.row-attach-name');
            if (pathInput) pathInput.value = row.attachmentPath || '';
            if (nameInput) nameInput.value  = row.attachmentOriginalName || '';
        }
    });

    updateTotals();
}

function setVal(tr, selector, value) {
    const el = tr.querySelector(selector);
    if (el && value !== null && value !== undefined) el.value = value;
}

// ================================================================
// INIT
// ================================================================
document.addEventListener('DOMContentLoaded', () => {
    const wfField = document.getElementById('workflowIdField');
    if (wfField && wfField.value) currentWorkflowId = wfField.value;

    // Load existing rows if in edit mode
    loadExistingRows();

    updateTotals();

    // ── Unsaved Changes Warning ──────────────────────────────────
    // Mark dirty when any input/select/textarea changes
    document.addEventListener('input',  () => { isDirty = true; });
    document.addEventListener('change', () => { isDirty = true; });

    // Warn on browser close / tab close / page refresh
    window.addEventListener('beforeunload', e => {
        if (isDirty) {
            e.preventDefault();
            e.returnValue = '';
        }
    });

    // Warn on sidebar nav links (in-app navigation)
    document.querySelectorAll('.sidebar-nav a').forEach(link => {
        link.addEventListener('click', e => {
            if (isDirty) {
                const ok = confirm(
                    'You have unsaved changes.\n\nIf you leave now, your changes will be lost.\n\nClick OK to leave, or Cancel to stay and save your draft.'
                );
                if (!ok) e.preventDefault();
            }
        });
    });
});
