/**
 * IFC E-Claims — Claim Form JavaScript
 * Handles: dynamic rows, auto-calculations, AJAX save/submit, row-level attachments
 */

let currentWorkflowId = null;

// Unsaved changes tracking
let isDirty = false;

// Pending attachments: array of {tr, file} waiting for rowId
const pendingAttachments = [];

const mealCalculationRequests = new Map();

// Date based on claim month
function getMonthRange() {
    if (!CLAIM_MONTH) return null;
    const [year, month] = CLAIM_MONTH.split('-').map(Number);
    if (!year || !month) return null;

    const min = `${CLAIM_MONTH}-01`;
    const lastDay = new Date(year, month, 0).getDate(); // last day of that month
    const max = `${CLAIM_MONTH}-${String(lastDay).padStart(2, '0')}`;

    return { min, max };
}

function formatMonthLabel(monthStr) {
    if (!monthStr) return '';
    const [year, month] = monthStr.split('-').map(Number);
    return new Date(year, month - 1, 1)
        .toLocaleString('en-US', { month: 'long', year: 'numeric' });
}

function applyDateConstraints(row) {
    const range = getMonthRange();
    if (!range) return;

    row.querySelectorAll('input[type="date"]').forEach(input => {
        input.min = range.min;
        input.max = range.max;

        input.addEventListener('change', () => {
            if (!input.value) return;
            if (input.value < range.min || input.value > range.max) {
                showToast(`Date must be within ${formatMonthLabel(CLAIM_MONTH)}`, 'error');
                input.value = '';
                input.dispatchEvent(new Event('change', { bubbles: false })); // avoid recursion, see note below
                updateTotals();
            }
        });
    });
}

function addRow(sectionId) {
    const tbody = document.querySelector(`#${sectionId} tbody`);
    if (!tbody) return;

    const existingRows = tbody.querySelectorAll('tr');
    const rowNum = existingRows.length + 1;

    // Get the previous row before adding the new row
    const previousRow = existingRows.length > 0
        ? existingRows[existingRows.length - 1]
        : null;

    // Create new row
    const row = document.createElement('tr');
    row.dataset.rowNum = rowNum;
    row.innerHTML = buildRowHtml(sectionId, rowNum);

    tbody.appendChild(row);
    applyDateConstraints(row);

    // Replicate previous data - only for others & mileage sections
    if (sectionId === 'othersSection' && previousRow) {
        replicateOthersRow(previousRow, row);
    }
    if (sectionId === 'mileageSection' && previousRow) {
        replicateMileageRow(previousRow, row);
    }

    updateTotals();
}

function replicateOthersRow(previousRow, newRow) {

    // ------------------------------------------------------------
    // Copy common fields
    // ------------------------------------------------------------

    // Date
    // New date = next date after previous row's date
    const previousDate =
        previousRow.querySelector('.row-date')?.value || '';

    if (previousDate) {
        const date = new Date(previousDate);

        // Add 1 day
        date.setDate(date.getDate() + 1);

        // Format as YYYY-MM-DD for input[type="date"]
        const nextDate = date.toISOString().split('T')[0];

        setVal(
            newRow,
            '.row-date',
            nextDate
        );
    } else {
        setVal(
            newRow,
            '.row-date',
            ''
        );
    }


    // Claim Type
    const previousClaimId =
        previousRow.querySelector('.row-claim-id')?.value || '';

    const newClaimSelect =
        newRow.querySelector('.row-claim-id');

    if (newClaimSelect) {
        newClaimSelect.value = previousClaimId;

        // Trigger claim type logic so the correct
        // fields are displayed/hidden
        onClaimTypeChange(newClaimSelect);
    }


    // Description
    setVal(
        newRow,
        '.others-desc',
        previousRow.querySelector('.others-desc')?.value || ''
    );


    // --------------------------------------------------------
    // Other Others claim types
    // --------------------------------------------------------

    // Amount should be blank
    setVal(
        newRow,
        '.row-amount',
        ''
    );

    // Total should be 0
    setVal(
        newRow,
        '.row-total',
        '0'
    );

    updateTotals();
}

function replicateMileageRow(previousRow, newRow) {

    // Date = next date after previous row's date
    const previousDate =
        previousRow.querySelector('.row-date')?.value || '';

    if (previousDate) {
        const date = new Date(previousDate);
        date.setDate(date.getDate() + 1);
        const nextDate = date.toISOString().split('T')[0];
        setVal(newRow, '.row-date', nextDate);
    } else {
        setVal(newRow, '.row-date', '');
    }

    // Description
    setVal(
        newRow,
        '.row-desc',
        previousRow.querySelector('.row-desc')?.value || ''
    );

    // Vehicle type
    setVal(
        newRow,
        '.row-vehicle',
        previousRow.querySelector('.row-vehicle')?.value || ''
    );

    // Distance
    setVal(
        newRow,
        '.row-km',
        previousRow.querySelector('.row-km')?.value || ''
    );

    // Recalculate mileage amount and total
    const vehicleSelect = newRow.querySelector('.row-vehicle');
    if (vehicleSelect) {
        calcMileageRow(vehicleSelect);
    }

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
        case 'mileageSection':
            return `
            <td><input type="date" class="row-input row-date" onchange="updateTotals()"/></td>
            <td><input type="text" class="row-input row-desc" placeholder="Description"/></td>
            <td>
                <select class="row-input row-vehicle" onchange="calcMileageRow(this)">
                    <option value="">Select Vehicle...</option>
                    <option value="Car">Car</option>
                    <option value="Motorcycle">Motorcycle</option>
                </select>
            </td>
            <td><input type="number" class="row-input row-km" placeholder="Distance (KM)" min="0" oninput="calcMileageRow(this)"/></td>
            <td><input type="number" class="row-input row-amount" step="0.01" readonly style="background:#f8fafc"/></td>
            <td><input type="text" class="row-input row-total" readonly style="background:#f8fafc;font-weight:600"/></td>
            <td>${buildAttachCell()}</td>
            <td><button type="button" class="btn-del-row" onclick="deleteRow(this)"><i class="bi bi-trash3"></i></button></td>`;

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
            <td><input type="text" class="row-input row-receipt" placeholder="Receipt No"/></td>
            <td><input type="number" class="row-input row-amount" step="0.01" min="0" placeholder="0.00" oninput="syncMedicalTotal(this)"/></td>
            <td><input type="text" class="row-input row-total" readonly style="background:#f8fafc;font-weight:600"/></td>
            <td>${buildAttachCell()}</td>
            <td><button type="button" class="btn-del-row" onclick="deleteRow(this)"><i class="bi bi-trash3"></i></button></td>`;

        case 'mealSection':
            return `
            <td><input type="date" class="row-input row-date" onchange="calcMealRow()"/></td>
            <td>${claimSelect}</td>
            <td><input type="text" class="row-input row-desc" placeholder="Description"/></td>
            <td><input type="time" class="row-input row-time-from" onchange="calcMealRow(this)"/></td>
            <td><input type="time" class="row-input row-time-to"   onchange="calcMealRow(this)"/></td>
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
        othersSection:  ['CL03','CL08','CL09','CL10','CL11']
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
    const tr = select.closest('tr');
    if (!tr) return;

    const id = select.value;

    const clinicDiv = tr.querySelector('.medical-clinic-cell');
    const amountInput = tr.querySelector('.row-amount');
    const totalInput = tr.querySelector('.row-total');

    const isMealRow = tr.closest('#mealSection') !== null;

    if (clinicDiv) clinicDiv.style.display = id === 'CL05' ? 'block' : 'none';

    // Meal Section Condition
    if (isMealRow) {

        calcMealRow(select);

        return;
    }

    // Other Section Condition — manual amount input
    if (amountInput) {
        amountInput.readOnly = false;
        amountInput.style.background = '#fff';
    }

    if (totalInput) {
        totalInput.value = '';
    }

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
    const tr = input.closest('tr');
    const total = tr.querySelector('.row-total');
    if (total) total.value = parseFloat(input.value || 0).toFixed(2);
    updateTotals();
}

async function calcMealRow(input) {
    const tr = input.closest('tr');
    if (!tr) return;

    const requestId = (mealCalculationRequests.get(tr) || 0) + 1;

    mealCalculationRequests.set(tr, requestId);

    const from = tr.querySelector('.row-time-from')?.value || '';
    const to = tr.querySelector('.row-time-to')?.value || '';
    const claimId = tr.querySelector('.row-claim-id')?.value || '';
    const amtInput = tr.querySelector('.row-amount');
    const totalInput = tr.querySelector('.row-total');
    const date = tr.querySelector('.row-date');

    // Clear calculation if claim type is invalid
    if (!claimId || !['CL01', 'CL02'].includes(claimId)) {

        if (amtInput) {
            amtInput.value = '';
        }

        if (totalInput) {
            totalInput.value = '';
        }

        updateTotals();
        return;
    }

    // Clear calculation if time is incomplete
    if (!from || !to) {

        if (amtInput) {
            amtInput.value = '';
        }

        if (totalInput) {
            totalInput.value = '';
        }

        updateTotals();
        return;
    }

    // Build API URL
    const params = new URLSearchParams({
        claimId: claimId,
        timeFrom: from,
        timeTo: to
    });

    try {
        const url = `/claims/api/calc/meal?${params.toString()}`;
        const response = await fetch(url);

        // Check backend response
        if (!response.ok) {
            throw new Error(
                `Meal calculation failed: ${response.status}`
            );
        }

        const data = await response.json();

        if (mealCalculationRequests.get(tr) !== requestId) {
            return;
        }

        const calculatedTotal = Number(data.total || 0).toFixed(2);

        // Update Amount
        if (amtInput) {
            amtInput.value = calculatedTotal;
        }

        // Update Row Total
        if (totalInput) {
            totalInput.value = calculatedTotal;
        }

        // Update Grand Total
        updateTotals();
    } catch (error) {

        console.error(
            'Meal calculation error:', error
        );

        if(mealCalculationRequests.get(tr) !== requestId) {
            return;
        }

        if (amtInput) {
            amtInput.value = '';
        }

        if (totalInput) {
            totalInput.value = '';
        }

        updateTotals();
    }
}

function calcMileageRow(input) {
    const tr = input.closest('tr');
    const vehicle = tr.querySelector('.row-vehicle')?.value;
    const km = parseInt(tr.querySelector('.row-km')?.value) || 0;
    if (!vehicle || !km) return;

    fetch(`/claims/api/calc/mileage?vehicleType=${vehicle}&km=${km}`)
        .then(r => r.json()).then(data => {
        const amtInput = tr.querySelector('.row-amount');
        const totalInput = tr.querySelector('.row-total');
        if (amtInput) amtInput.value = data.total;
        if (totalInput) totalInput.value = data.total;
        updateTotals();
    }).catch(() => {
    });
}

function calcTravelRow(select) {
    const tr = select.closest('tr');
    const travelId = tr.querySelector('.row-travel-id')?.value;
    const mealId = tr.querySelector('.row-meal-id')?.value;
    if (!travelId || !mealId) return;

    fetch(`/claims/api/calc/travel?travelId=${travelId}&mealId=${mealId}`)
        .then(r => r.json()).then(data => {
        const amtInput = tr.querySelector('.row-amount');
        const totalInput = tr.querySelector('.row-total');
        if (amtInput) amtInput.value = data.claimLimit;
        if (totalInput) totalInput.value = data.total;
        updateTotals();
    }).catch(() => {
    });
}

function updateTotals() {
    const sections = ['mileageSection', 'mealSection', 'medicalSection', 'travelSection', 'othersSection'];
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

    const tr = input.closest('tr');
    const btn = input.parentElement.querySelector('.btn-attach');
    const nameInput = input.parentElement.querySelector('.row-attach-name');
    const rowId = tr.dataset.rowId;

    if (rowId && currentWorkflowId) {
        await uploadFileToRow(rowId, file, btn);
    } else {
        const idx = pendingAttachments.findIndex(p => p.tr === tr);
        if (idx >= 0) pendingAttachments.splice(idx, 1);
        pendingAttachments.push({tr, file});

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
        const res = await fetch(`/claims/row/${rowId}/attachment`, {
            method: 'POST', headers: csrfHeader(), body: formData
        });
        const data = await res.json();
        if (data.success) {
            if (btn) {
                btn.classList.add('has-file');
                btn.title = file.name;
            }
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
        const {tr, file} = pendingAttachments[i];
        const rowId = tr.dataset.rowId;
        if (rowId) {
            const btn = tr.querySelector('.btn-attach');
            const ok = await uploadFileToRow(rowId, file, btn);
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
        {id: 'mileageSection', type: 'mileage'},
        {id: 'medicalSection', type: 'medical'},
        {id: 'mealSection', type: 'meal'},
        {id: 'travelSection', type: 'travel'},
        {id: 'othersSection', type: 'others'}
    ];

    sections.forEach(({id}) => {
        const tbody = document.querySelector(`#${id} tbody`);
        if (!tbody) return;
        tbody.querySelectorAll('tr').forEach(tr => {
            const row = {
                rowId: tr.dataset.rowId || null,
                claimId: tr.querySelector('.row-claim-id')?.value
                    || (id === 'travelSection' ? 'CL12' : null)
                    || (id === 'mileageSection' ? 'CL04' : null),
                date: tr.querySelector('.row-date')?.value || null,
                description: tr.querySelector('.row-desc')?.value || null,
                receiptNo: tr.querySelector('.row-receipt')?.value || null,
                projectManagerId: tr.querySelector('.row-pm')?.value || null,
                timeFrom: tr.querySelector('.row-time-from')?.value || null,
                timeTo: tr.querySelector('.row-time-to')?.value || null,
                medicalClinic: tr.querySelector('.row-panel-clinic')?.value || null,
                mileageVehicleType: tr.querySelector('.row-vehicle')?.value || null,
                mileageKm: id === 'mileageSection' ? (parseInt(tr.querySelector('.row-km')?.value) || null) : null,
                travelId: tr.querySelector('.row-travel-id')?.value || null,
                mealId: tr.querySelector('.row-meal-id')?.value || null,
                amount: parseFloat(tr.querySelector('.row-amount')?.value) || null,
                total: parseFloat(tr.querySelector('.row-total')?.value) || null,
                attachmentPath: tr.querySelector('.row-attach-path')?.value || null,
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
    if (rows.length === 0) {
        showToast('Add at least one claim row', 'error');
        return;
    }
    try {
        const res = await fetch('/claims/save-draft', {
            method: 'POST',
            headers: {'Content-Type': 'application/json', ...csrfHeader()},
            body: JSON.stringify({rows, workflowId: currentWorkflowId})
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
    } catch (e) {
        showToast('Save failed: ' + e.message, 'error');
    }
}

// ================================================================
// REFRESH ROW IDS
// ================================================================

async function refreshRowIds(workflowId) {
    try {
        const res = await fetch(`/claims/api/rows/${workflowId}`, {
            headers: csrfHeader()
        });
        if (!res.ok) return;
        const savedRows = await res.json();

        const sections = ['mileageSection', 'medicalSection', 'mealSection', 'travelSection', 'othersSection'];
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
                    || (sectionId === 'travelSection' ? 'CL12' : null)
                    || (sectionId === 'mileageSection' ? 'CL04' : null);
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
        const res = await fetch(`/claims/${currentWorkflowId}/submit`, {
            method: 'POST', headers: csrfHeader()
        });
        const data = await res.json();
        if (data.success) {
            showToast('Claim submitted successfully!', 'success');
            setTimeout(() => window.location.href = data.redirectUrl || '/dashboard', 1500);
        } else {
            showToast(data.message || 'Submit failed', 'error');
        }
    } catch (e) {
        showToast('Submit failed: ' + e.message, 'error');
    }
}

// ================================================================
// CSRF + TOAST
// ================================================================

function csrfHeader() {
    const meta = document.querySelector('meta[name="_csrf"]');
    const header = document.querySelector('meta[name="_csrf_header"]');
    if (meta && header) return {[header.content]: meta.content};
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
        if (id === 'CL04')                          sectionId = 'mileageSection';
        else if (['CL05','CL06','CL07','CL13','CL14'].includes(id)) sectionId = 'medicalSection';
        else if (['CL01','CL02'].includes(id))      sectionId = 'mealSection';
        else if (id === 'CL12')                     sectionId = 'travelSection';
        else                                        sectionId = 'othersSection';

        addRow(sectionId);

        const tbody = document.querySelector(`#${sectionId} tbody`);
        const tr = tbody.lastElementChild;
        if (!tr) return;

        if (row.rowId) tr.dataset.rowId = row.rowId;

        setVal(tr, '.row-date', row.date ? row.date.substring(0, 10) : '');
        setVal(tr, '.row-desc', row.description || '');
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
            setVal(tr, '.row-receipt', row.receiptNo || '')
            const amtEl = tr.querySelector('.row-amount');
            const totEl = tr.querySelector('.row-total');
            if (amtEl && totEl && amtEl.value && !totEl.value) {
                totEl.value = parseFloat(amtEl.value).toFixed(2);
            }
        }

        if (sectionId === 'mealSection') {

            setVal(tr, '.row-time-from', row.timeFrom || '');
            setVal(tr, '.row-time-to', row.timeTo || '');
            setVal(tr, '.row-pm', row.projectManagerId || '');


            const claimSel = tr.querySelector('.row-claim-id');
            const timeFrom = tr.querySelector('.row-time-from');
            const timeTo = tr.querySelector('.row-time-to');

            if (claimSel &&
                ['CL01', 'CL02'].includes(claimSel.value) &&
                timeFrom?.value &&
                timeTo?.value
            ) {
                calcMealRow(claimSel);
            }
        }

        if (sectionId === 'travelSection') {
            setVal(tr, '.row-travel-id', row.travelId || '');
            setVal(tr, '.row-meal-id', row.mealId || '');
        }

        if (sectionId === 'mileageSection') {
            setVal(tr, '.row-vehicle', row.mileageVehicleType || '');
            setVal(tr, '.row-km', row.mileageKm || '');
            const vehicleSel = tr.querySelector('.row-vehicle');
            if (vehicleSel && vehicleSel.value && tr.querySelector('.row-km')?.value) {
                calcMileageRow(vehicleSel);
            }
        }

        if (sectionId === 'othersSection') {
            const amtEl = tr.querySelector('.row-amount');
            const totEl = tr.querySelector('.row-total');
            if (amtEl && totEl && amtEl.value) {
                totEl.value = parseFloat(amtEl.value).toFixed(2);
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
            if (nameInput) nameInput.value = row.attachmentOriginalName || '';
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
    document.addEventListener('input', () => {
        isDirty = true;
    });
    document.addEventListener('change', () => {
        isDirty = true;
    });

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