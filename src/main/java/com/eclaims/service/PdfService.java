package com.eclaims.service;

import com.eclaims.dto.ClaimFormDto;
import com.eclaims.dto.ClaimRowDto;
import com.eclaims.dto.StaffClaimBalanceDto;
import com.eclaims.entity.*;
import com.eclaims.repository.*;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfService {

    private final ClaimService              claimService;
    private final ClaimFormRowRepository    rowRepository;
    private final TravelLocationRepository  travelLocationRepository;
    private final TravelMealRepository      travelMealRepository;
    private final ClaimTypeRepository       claimTypeRepository;
    private final StaffInfoRepository       staffInfoRepository;
    private final DepartmentRepository      departmentRepository;
    private final CompanyRepository         companyRepository;

    @Value("${eclaims.upload.path:C:/eclaims-uploads/}")
    private String uploadPath;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ================================================================
    // PUBLIC ENTRY POINTS
    // ================================================================

    /** Summary only — no attachments */
    public byte[] generateSummaryOnlyPdf(String workflowId) throws Exception {
        ClaimFormDto form = claimService.loadForm(workflowId);
        List<StaffClaimBalanceDto> balances = claimService.getBalances(form.getStaffId());
        return generateSummaryPdf(form, balances);
    }

    /** Full report — claim form with attachments embedded section by section */
    public byte[] generateFullPdf(String workflowId) throws Exception {
        ClaimFormDto form    = claimService.loadForm(workflowId);
        List<StaffClaimBalanceDto> balances = claimService.getBalances(form.getStaffId());
        List<ClaimFormRow> rows = rowRepository.findByWorkflowIdOrderByCreatedDate(workflowId);

        // Log attachment status
        rows.forEach(r -> {
            if (r.getAttachmentPath() != null && !r.getAttachmentPath().isBlank()) {
                Path p = Paths.get(uploadPath, r.getAttachmentPath());
                log.info("Attachment row {}: path=[{}] exists={}", r.getRowId(), r.getAttachmentPath(), Files.exists(p));
            } else {
                log.info("Attachment row {}: NO attachment (claimId={})", r.getRowId(), r.getClaimId());
            }
        });

        // Build map: claimId -> ClaimFormRow (for attachment lookup)
        Map<Integer, ClaimFormRow> rowMap = rows.stream()
            .filter(r -> r.getRowId() != null)
            .collect(Collectors.toMap(ClaimFormRow::getRowId, r -> r));

        return generateFullReportPdf(form, balances, rows);
    }

    // ================================================================
    // FULL REPORT PDF — section-per-page with receipts below each table
    // ================================================================

    /**
     * Layout:
     * Page 1  : Claim summary header (staff info, approval policy)
     * Page 2  : Medical table + "test - receipt" label → Page 3: receipt file
     * Page N  : Meal table + "team lunch - receipt" label → Page N+1: receipt file
     * Page M  : Travel table + receipt label → receipt file
     * Page K  : Others table + receipt label → receipt file
     * Last    : Grand Total + Claim Records + Signatures
     */
    private byte[] generateFullReportPdf(ClaimFormDto form,
                                          List<StaffClaimBalanceDto> balances,
                                          List<ClaimFormRow> allRows) throws Exception {

        // Group DB rows by section for attachment lookup
        Map<String, List<ClaimFormRow>> dbRowsBySection = new LinkedHashMap<>();
        dbRowsBySection.put("MEDICAL", new ArrayList<>());
        dbRowsBySection.put("MEAL",    new ArrayList<>());
        dbRowsBySection.put("TRAVEL",  new ArrayList<>());
        dbRowsBySection.put("OTHERS",  new ArrayList<>());
        for (ClaimFormRow r : allRows) {
            switch (r.getClaimId()) {
                case "CL05","CL06","CL07","CL13","CL14" -> dbRowsBySection.get("MEDICAL").add(r);
                case "CL01","CL02"         -> dbRowsBySection.get("MEAL").add(r);
                case "CL12"                -> dbRowsBySection.get("TRAVEL").add(r);
                default                    -> dbRowsBySection.get("OTHERS").add(r);
            }
        }

        // Group DTO rows for table rendering
        Map<String, List<ClaimRowDto>> grouped = groupRowsBySection(form.getRows());

        // ── Step 1: Build page 1 — summary header only ──
        byte[] page1 = buildHeaderPage(form);

        // ── Step 2: Build per-section pages ──
        // Each section = one iText page: section table + receipt labels at bottom
        // Followed by the actual receipt files (PDFBox merge)

        PDFMergerUtility merger = new PDFMergerUtility();
        ByteArrayOutputStream finalOut = new ByteArrayOutputStream();
        merger.setDestinationStream(finalOut);
        merger.addSource(new org.apache.pdfbox.io.RandomAccessReadBuffer(page1));

        String[][] sections = {
            {"MEDICAL", "Medical / Dental / Optical Claim"},
            {"MEAL",    "Meal Allowance"},
            {"TRAVEL",  "Travel Allowance"},
            {"OTHERS",  "Others"}
        };

        com.itextpdf.text.Font fHead  = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  9, new BaseColor(37,99,235));
        com.itextpdf.text.Font fTH    = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  7, BaseColor.WHITE);
        com.itextpdf.text.Font fTD    = FontFactory.getFont(FontFactory.HELVETICA,       7, BaseColor.BLACK);

        for (String[] sec : sections) {
            String key         = sec[0];
            String sectionName = sec[1];

            List<ClaimRowDto> dtoRows = grouped.getOrDefault(key, List.of());
            List<ClaimFormRow> dbRows = dbRowsBySection.get(key);
            List<ClaimFormRow> withAttach = dbRows.stream()
                .filter(r -> r.getAttachmentPath() != null && !r.getAttachmentPath().isBlank())
                .filter(r -> Files.exists(Paths.get(uploadPath, r.getAttachmentPath())))
                .collect(Collectors.toList());

            // Build section page: header bar + section table + receipt list labels
            byte[] sectionPage = buildSectionPage(sectionName, key, dtoRows, withAttach, fHead, fTH, fTD);
            merger.addSource(new org.apache.pdfbox.io.RandomAccessReadBuffer(sectionPage));

            // Append each actual receipt file after section page
            for (ClaimFormRow row : withAttach) {
                Path p = Paths.get(uploadPath, row.getAttachmentPath());
                String lower = p.toString().toLowerCase();
                if (lower.endsWith(".pdf")) {
                    merger.addSource(new org.apache.pdfbox.io.RandomAccessReadBufferedFile(p.toFile()));
                } else {
                    merger.addSource(new org.apache.pdfbox.io.RandomAccessReadBuffer(imageToPdf(p)));
                }
            }
        }

        // ── Step 3: Grand Total + Claim Records + Signatures page ──
        byte[] footerPage = buildFooterPage(form, balances);
        merger.addSource(new org.apache.pdfbox.io.RandomAccessReadBuffer(footerPage));

        merger.mergeDocuments(null);
        return finalOut.toByteArray();
    }

    // ── Page 1: Header (staff info + approval policy) ──────────
    private byte[] buildHeaderPage(ClaimFormDto form) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 50, 40);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        doc.open();

        com.itextpdf.text.Font fTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new BaseColor(15,21,35));
        com.itextpdf.text.Font fLabel = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  8, BaseColor.DARK_GRAY);
        com.itextpdf.text.Font fValue = FontFactory.getFont(FontFactory.HELVETICA,       8, BaseColor.BLACK);

        // Top bar
        PdfPTable headerBar = new PdfPTable(2);
        headerBar.setWidthPercentage(100); headerBar.setWidths(new float[]{3f, 1.5f}); headerBar.setSpacingAfter(10);
        PdfPCell titleCell = new PdfPCell();
        titleCell.setBackgroundColor(new BaseColor(15,21,35)); titleCell.setBorder(Rectangle.NO_BORDER); titleCell.setPadding(10);
        titleCell.addElement(new Phrase("E-CLAIM APPLY FORM", fTitle));
        PdfPCell brandCell = new PdfPCell(new Phrase("InfoConnect",
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, BaseColor.WHITE)));
        brandCell.setBackgroundColor(new BaseColor(220,38,38)); brandCell.setBorder(Rectangle.NO_BORDER);
        brandCell.setPadding(10); brandCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        brandCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        headerBar.addCell(titleCell); headerBar.addCell(brandCell);
        doc.add(headerBar);

        // Staff info + approval policy
        PdfPTable infoTbl = new PdfPTable(2);
        infoTbl.setWidthPercentage(100); infoTbl.setWidths(new float[]{2.5f, 1.5f}); infoTbl.setSpacingAfter(10);
        PdfPCell staffCell = new PdfPCell();
        staffCell.setBackgroundColor(new BaseColor(245, 247, 250)); staffCell.setBorder(Rectangle.BOX); staffCell.setPadding(8);
        staffCell.setBorderColor(new BaseColor(200, 210, 225));
        com.itextpdf.text.Font fSL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, new BaseColor(80, 80, 80));
        com.itextpdf.text.Font fSV = FontFactory.getFont(FontFactory.HELVETICA, 8, BaseColor.BLACK);
        com.itextpdf.text.Font fSH = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new BaseColor(59, 130, 246));
        staffCell.addElement(new Phrase("STAFF CLAIM", fSH)); staffCell.addElement(Chunk.NEWLINE);
        addInfoLine(staffCell,"Staff ID",    form.getStaffId(),    fSL,fSV);
        addInfoLine(staffCell,"Name",        form.getStaffName(),  fSL,fSV);
        addInfoLine(staffCell,"Department",  form.getDepartment(), fSL,fSV);
        addInfoLine(staffCell,"Company",     form.getCompany(),    fSL,fSV);
        addInfoLine(staffCell,"Designation", form.getDesignation(),fSL,fSV);
        addInfoLine(staffCell,"Form No.",    form.getFormId(),     fSL,fSV);
        addInfoLine(staffCell,"Date",        LocalDate.now().format(DATE_FMT), fSL,fSV);
        String statusText = form.getWfStatus() != null ? form.getWfStatus().replace("_"," ") : "DRAFT";
        staffCell.addElement(new Phrase("Status: " + statusText,
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, getStatusColor(form.getWfStatus()))));
        infoTbl.addCell(staffCell);
        PdfPCell approvalCell = new PdfPCell();
        approvalCell.setBorder(Rectangle.BOX); approvalCell.setBorderColor(new BaseColor(37,99,235)); approvalCell.setPadding(6);
        com.itextpdf.text.Font fApprHead = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, BaseColor.WHITE);
        PdfPTable approvalInner = new PdfPTable(2); approvalInner.setWidthPercentage(100);
        addApprovalHeader(approvalInner,"Claim Approval Policy for Admin",fApprHead);
        addApprovalRow(approvalInner,"Level","Authorized Superior",fLabel,fValue,true);
        addApprovalRow(approvalInner,"1","Project Manager",fLabel,fValue,false);
        addApprovalRow(approvalInner,"2",form.getApproverName()!=null?form.getApproverName():form.getApproverId(),fLabel,fValue,false);
        addApprovalRow(approvalInner,"3","Finance",fLabel,fValue,false);
        approvalCell.addElement(approvalInner);
        infoTbl.addCell(approvalCell);
        doc.add(infoTbl);

        if (form.getWfStatus() != null && !"FINANCE_PROCESSED".equals(form.getWfStatus()))
            addWatermark(writer, form.getWfStatus().replace("_"," "));
        doc.close();
        return out.toByteArray();
    }

    // ── Section page: table + receipt list ─────────────────────
    private byte[] buildSectionPage(String sectionName, String key,
                                     List<ClaimRowDto> dtoRows,
                                     List<ClaimFormRow> withAttach,
                                     com.itextpdf.text.Font fHead,
                                     com.itextpdf.text.Font fTH,
                                     com.itextpdf.text.Font fTD) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 50, 40);
        PdfWriter.getInstance(doc, out);
        doc.open();

        // Mini header bar
        PdfPTable bar = new PdfPTable(2);
        bar.setWidthPercentage(100); bar.setWidths(new float[]{3f,1.5f}); bar.setSpacingAfter(12);
        PdfPCell bc = new PdfPCell();
        bc.setBackgroundColor(new BaseColor(15,21,35)); bc.setBorder(Rectangle.NO_BORDER); bc.setPadding(8);
        bc.addElement(new Phrase("E-CLAIM APPLY FORM",
            FontFactory.getFont(FontFactory.HELVETICA_BOLD,11,new BaseColor(15,21,35))));
        PdfPCell brc = new PdfPCell(new Phrase("InfoConnect",
            FontFactory.getFont(FontFactory.HELVETICA_BOLD,9,BaseColor.WHITE)));
        brc.setBackgroundColor(new BaseColor(220,38,38)); brc.setBorder(Rectangle.NO_BORDER);
        brc.setPadding(8); brc.setHorizontalAlignment(Element.ALIGN_RIGHT); brc.setVerticalAlignment(Element.ALIGN_MIDDLE);
        bar.addCell(bc); bar.addCell(brc);
        doc.add(bar);

        // Section table
        switch (key) {
            case "MEDICAL" -> addClaimSection(doc, sectionName,
                new String[]{"Date","Claim Type","Description","Amount (RM)","Total (RM)"},
                new float[]{1.2f,1.8f,3f,1.2f,1.2f}, dtoRows, "medical", fHead, fTH, fTD);
            case "MEAL" -> addClaimSection(doc, sectionName,
                new String[]{"Date","Claim Type","Description","Time From","Time To","Amount (RM)","Total (RM)"},
                new float[]{1.1f,1.5f,2f,0.9f,0.9f,1.1f,1.1f}, dtoRows, "meal", fHead, fTH, fTD);
            case "TRAVEL" -> addClaimSection(doc, sectionName,
                new String[]{"Date","Claim Type","Description","Meal","Amount (RM)","Total (RM)"},
                new float[]{1.1f,1.5f,2f,1.5f,1.1f,1.1f}, dtoRows, "travel", fHead, fTH, fTD);
            default -> addClaimSection(doc, sectionName,
                new String[]{"Date","Claim Type","Description","Amount (RM)","Total (RM)"},
                new float[]{1.2f,1.8f,3f,1.2f,1.2f}, dtoRows, "others", fHead, fTH, fTD);
        }

        // Receipt list below table
        if (!withAttach.isEmpty()) {
            doc.add(Chunk.NEWLINE);
            com.itextpdf.text.Font fRecHead = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, new BaseColor(37,99,235));
            com.itextpdf.text.Font fRecItem = FontFactory.getFont(FontFactory.HELVETICA, 8, new BaseColor(15,21,35));
            com.itextpdf.text.Font fRecNote = FontFactory.getFont(FontFactory.HELVETICA, 7, new BaseColor(100,116,139));

            Paragraph recTitle = new Paragraph("Supporting Documents", fRecHead);
            recTitle.setSpacingBefore(6); recTitle.setSpacingAfter(6);
            doc.add(recTitle);

            for (int i = 0; i < withAttach.size(); i++) {
                ClaimFormRow row = withAttach.get(i);
                String label = buildAttachmentLabel(row);
                Paragraph item = new Paragraph(
                    (i+1) + ".  " + label + "  —  see attached receipt on next page", fRecItem);
                item.setSpacingAfter(3);
                doc.add(item);
            }

            Paragraph note = new Paragraph(
                "Receipts are attached on the following page(s) in order listed above.", fRecNote);
            note.setSpacingBefore(8);
            doc.add(note);
        }

        doc.close();
        return out.toByteArray();
    }

    // ── Footer page: Grand Total + Balances + Signatures ───────
    private byte[] buildFooterPage(ClaimFormDto form,
                                    List<StaffClaimBalanceDto> balances) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 50, 40);
        PdfWriter.getInstance(doc, out);
        doc.open();

        com.itextpdf.text.Font fHead  = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  9, new BaseColor(37,99,235));
        com.itextpdf.text.Font fTH    = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  7, BaseColor.WHITE);
        com.itextpdf.text.Font fTD    = FontFactory.getFont(FontFactory.HELVETICA,       7, BaseColor.BLACK);
        com.itextpdf.text.Font fTotal = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  9, BaseColor.WHITE);
        com.itextpdf.text.Font fLabel = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  8, BaseColor.DARK_GRAY);

        // Mini header
        PdfPTable bar = new PdfPTable(2);
        bar.setWidthPercentage(100); bar.setWidths(new float[]{3f,1.5f}); bar.setSpacingAfter(12);
        PdfPCell bc = new PdfPCell();
        bc.setBackgroundColor(new BaseColor(15,21,35)); bc.setBorder(Rectangle.NO_BORDER); bc.setPadding(8);
        bc.addElement(new Phrase("E-CLAIM APPLY FORM",
            FontFactory.getFont(FontFactory.HELVETICA_BOLD,11,new BaseColor(15,21,35))));
        PdfPCell brc = new PdfPCell(new Phrase("InfoConnect",
            FontFactory.getFont(FontFactory.HELVETICA_BOLD,9,BaseColor.WHITE)));
        brc.setBackgroundColor(new BaseColor(220,38,38)); brc.setBorder(Rectangle.NO_BORDER);
        brc.setPadding(8); brc.setHorizontalAlignment(Element.ALIGN_RIGHT); brc.setVerticalAlignment(Element.ALIGN_MIDDLE);
        bar.addCell(bc); bar.addCell(brc);
        doc.add(bar);

        // Grand Total
        PdfPTable grandTbl = new PdfPTable(1);
        grandTbl.setWidthPercentage(50); grandTbl.setHorizontalAlignment(Element.ALIGN_RIGHT); grandTbl.setSpacingAfter(12);
        PdfPCell gtCell = new PdfPCell(new Phrase("TOTAL CLAIMS AMOUNT  RM " + fmt(form.getTotalClaim()), fTotal));
        gtCell.setBackgroundColor(new BaseColor(15,21,35)); gtCell.setPadding(10);
        gtCell.setHorizontalAlignment(Element.ALIGN_CENTER); gtCell.setBorder(Rectangle.BOX);
        grandTbl.addCell(gtCell);
        doc.add(grandTbl);

        // Claim Records
        if (balances != null && !balances.isEmpty()) {
            doc.add(sectionTitle("CLAIM RECORDS", fHead));
            PdfPTable balTbl = new PdfPTable(5);
            balTbl.setWidthPercentage(100); balTbl.setWidths(new float[]{3f,1.5f,1.5f,1.5f,1f}); balTbl.setSpacingAfter(10);
            for (String h : new String[]{"Type","Enti/Year","Claimed","Balance","Action"}) {
                PdfPCell c = new PdfPCell(new Phrase(h, fTH));
                c.setBackgroundColor(new BaseColor(15,21,35)); c.setPadding(5); balTbl.addCell(c);
            }
            for (StaffClaimBalanceDto b : balances) {
                BaseColor rowBg = b.getBalance()!=null && b.getBalance().compareTo(BigDecimal.ZERO)<0
                    ? new BaseColor(254,242,242) : BaseColor.WHITE;
                addBalanceRow(balTbl, b.getClaimTitle(), fmt(b.getEntitled()),
                    fmt(b.getClaimed()), fmt(b.getBalance()), fTD, rowBg);
            }
            doc.add(balTbl);
        }

        // Signatures
        doc.add(Chunk.NEWLINE);
        PdfPTable sigTbl = new PdfPTable(3);
        sigTbl.setWidthPercentage(100);
        for (String lbl : new String[]{"Submitted by:","Verified by:","Approved by:"}) {
            PdfPCell c = new PdfPCell();
            c.setBorder(Rectangle.NO_BORDER); c.setBorderWidthBottom(1f);
            c.setBorderColorBottom(BaseColor.LIGHT_GRAY);
            c.setPaddingBottom(28); c.setPaddingTop(6);
            c.setPhrase(new Phrase(lbl, fLabel));
            sigTbl.addCell(c);
        }
        doc.add(sigTbl);

        doc.close();
        return out.toByteArray();
    }

    /** Build label: Description → MedicalClinic → TravelLocation → ClaimType */
    private String buildAttachmentLabel(ClaimFormRow row) {
        if (row.getDescription() != null && !row.getDescription().isBlank())
            return row.getDescription().trim();
        if (row.getMedicalClinic() != null && !row.getMedicalClinic().isBlank())
            return row.getMedicalClinic().trim();
        if (row.getTravelId() != null && !row.getTravelId().isBlank())
            return travelLocationRepository.findById(row.getTravelId())
                .map(TravelLocation::getLocation).orElse(row.getTravelId());
        return claimTypeRepository.findById(row.getClaimId())
            .map(ClaimType::getClaimTitle).orElse(row.getClaimId());
    }

    // ================================================================
    // SUMMARY PDF (iText) — unchanged from working version
    // ================================================================

    private byte[] generateSummaryPdf(ClaimFormDto form,
                                       List<StaffClaimBalanceDto> balances) throws DocumentException, IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 50, 40);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        doc.open();

        com.itextpdf.text.Font fTitle  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new BaseColor(15,21,35));
        com.itextpdf.text.Font fHead   = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  9, new BaseColor(37,99,235));
        com.itextpdf.text.Font fLabel  = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  8, BaseColor.DARK_GRAY);
        com.itextpdf.text.Font fValue  = FontFactory.getFont(FontFactory.HELVETICA,       8, BaseColor.BLACK);
        com.itextpdf.text.Font fTH     = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  7, BaseColor.WHITE);
        com.itextpdf.text.Font fTD     = FontFactory.getFont(FontFactory.HELVETICA,       7, BaseColor.BLACK);
        com.itextpdf.text.Font fTotal  = FontFactory.getFont(FontFactory.HELVETICA_BOLD,  9, BaseColor.WHITE);

        PdfPTable headerBar = new PdfPTable(2);
        headerBar.setWidthPercentage(100);
        headerBar.setWidths(new float[]{3f, 1.5f});
        headerBar.setSpacingAfter(10);
        PdfPCell titleCell = new PdfPCell();
        titleCell.setBackgroundColor(new BaseColor(15, 21, 35));
        titleCell.setBorder(Rectangle.NO_BORDER); titleCell.setPadding(10);
        titleCell.addElement(new Phrase("E-CLAIM APPLY FORM", fTitle));
        com.itextpdf.text.Font fWhite = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, BaseColor.WHITE);
        PdfPCell brandCell = new PdfPCell(new Phrase("InfoConnect", fWhite));
        brandCell.setBackgroundColor(new BaseColor(220, 38, 38));
        brandCell.setBorder(Rectangle.NO_BORDER); brandCell.setPadding(10);
        brandCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        brandCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        headerBar.addCell(titleCell); headerBar.addCell(brandCell);
        doc.add(headerBar);

        PdfPTable infoTbl = new PdfPTable(2);
        infoTbl.setWidthPercentage(100);
        infoTbl.setWidths(new float[]{2.5f, 1.5f});
        infoTbl.setSpacingAfter(10);

        PdfPCell staffCell = new PdfPCell();
        staffCell.setBackgroundColor(new BaseColor(245, 247, 250)); // light background
        staffCell.setBorder(Rectangle.BOX); staffCell.setPadding(8);
        staffCell.setBorderColor(new BaseColor(200, 210, 225));
        com.itextpdf.text.Font fStaffLabel = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, new BaseColor(80, 80, 80));
        com.itextpdf.text.Font fStaffValue = FontFactory.getFont(FontFactory.HELVETICA, 8, BaseColor.BLACK);
        com.itextpdf.text.Font fStaffHead  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new BaseColor(59, 130, 246));
        staffCell.addElement(new Phrase("STAFF CLAIM", fStaffHead));
        staffCell.addElement(Chunk.NEWLINE);
        addInfoLine(staffCell, "Staff ID",    form.getStaffId(),    fStaffLabel, fStaffValue);
        addInfoLine(staffCell, "Name",        form.getStaffName(),  fStaffLabel, fStaffValue);
        addInfoLine(staffCell, "Department",  form.getDepartment(), fStaffLabel, fStaffValue);
        addInfoLine(staffCell, "Company",     form.getCompany(),    fStaffLabel, fStaffValue);
        addInfoLine(staffCell, "Designation", form.getDesignation(),fStaffLabel, fStaffValue);
        addInfoLine(staffCell, "Form No.",    form.getFormId(),     fStaffLabel, fStaffValue);
        addInfoLine(staffCell, "Date",        LocalDate.now().format(DATE_FMT), fStaffLabel, fStaffValue);
        String statusText = form.getWfStatus() != null ? form.getWfStatus().replace("_", " ") : "DRAFT";
        BaseColor statusColor = getStatusColor(form.getWfStatus());
        com.itextpdf.text.Font fStatus = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, statusColor);
        staffCell.addElement(new Phrase("\nStatus: " + statusText, fStatus));
        infoTbl.addCell(staffCell);

        PdfPCell approvalCell = new PdfPCell();
        approvalCell.setBorder(Rectangle.BOX);
        approvalCell.setBorderColor(new BaseColor(37, 99, 235)); approvalCell.setPadding(6);
        com.itextpdf.text.Font fApprHead = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, BaseColor.WHITE);
        PdfPTable approvalInner = new PdfPTable(2);
        approvalInner.setWidthPercentage(100);
        addApprovalHeader(approvalInner, "Claim Approval Policy for Admin", fApprHead);
        addApprovalRow(approvalInner, "Level", "Authorized Superior", fLabel, fValue, true);
        addApprovalRow(approvalInner, "1", "Project Manager", fLabel, fValue, false);
        addApprovalRow(approvalInner, "2", form.getApproverName() != null ? form.getApproverName() : form.getApproverId(), fLabel, fValue, false);
        addApprovalRow(approvalInner, "3", "Finance", fLabel, fValue, false);
        approvalCell.addElement(approvalInner);
        infoTbl.addCell(approvalCell);
        doc.add(infoTbl);

        Map<String, List<ClaimRowDto>> grouped = groupRowsBySection(form.getRows());
        List<ClaimRowDto> medRows    = grouped.getOrDefault("MEDICAL", List.of());
        List<ClaimRowDto> mealRows   = grouped.getOrDefault("MEAL",    List.of());
        List<ClaimRowDto> travelRows = grouped.getOrDefault("TRAVEL",  List.of());
        List<ClaimRowDto> otherRows  = grouped.getOrDefault("OTHERS",  List.of());

        addClaimSection(doc, "Medical / Dental / Optical Claim",
            new String[]{"Date","Claim Type","Description","Amount (RM)","Total (RM)"},
            new float[]{1.2f, 1.8f, 3f, 1.2f, 1.2f},
            medRows, "medical", fHead, fTH, fTD);
        addClaimSection(doc, "Meal Allowance",
            new String[]{"Date","Claim Type","Description","Time From","Time To","Amount (RM)","Total (RM)"},
            new float[]{1.1f, 1.5f, 2f, 0.9f, 0.9f, 1.1f, 1.1f},
            mealRows, "meal", fHead, fTH, fTD);
        addClaimSection(doc, "Travel Allowance",
            new String[]{"Date","Claim Type","Description","Meal","Amount (RM)","Total (RM)"},
            new float[]{1.1f, 1.5f, 2f, 1.5f, 1.1f, 1.1f},
            travelRows, "travel", fHead, fTH, fTD);
        addClaimSection(doc, "Others",
            new String[]{"Date","Claim Type","Description","Amount (RM)","Total (RM)"},
            new float[]{1.2f, 1.8f, 3f, 1.2f, 1.2f},
            otherRows, "others", fHead, fTH, fTD);

        doc.add(Chunk.NEWLINE);
        PdfPTable grandTbl = new PdfPTable(1);
        grandTbl.setWidthPercentage(50);
        grandTbl.setHorizontalAlignment(Element.ALIGN_RIGHT);
        grandTbl.setSpacingAfter(12);
        PdfPCell gtCell = new PdfPCell(new Phrase("TOTAL CLAIMS AMOUNT  RM " + fmt(form.getTotalClaim()), fTotal));
        gtCell.setBackgroundColor(new BaseColor(15, 21, 35)); gtCell.setPadding(10);
        gtCell.setHorizontalAlignment(Element.ALIGN_CENTER); gtCell.setBorder(Rectangle.BOX);
        grandTbl.addCell(gtCell);
        doc.add(grandTbl);

        if (balances != null && !balances.isEmpty()) {
            doc.add(sectionTitle("CLAIM RECORDS", fHead));
            PdfPTable balTbl = new PdfPTable(5);
            balTbl.setWidthPercentage(100);
            balTbl.setWidths(new float[]{3f, 1.5f, 1.5f, 1.5f, 1f});
            balTbl.setSpacingAfter(10);
            for (String h : new String[]{"Type","Enti/Year","Claimed","Balance","Action"}) {
                PdfPCell c = new PdfPCell(new Phrase(h, fTH));
                c.setBackgroundColor(new BaseColor(15, 21, 35)); c.setPadding(5);
                balTbl.addCell(c);
            }
            for (StaffClaimBalanceDto b : balances) {
                BaseColor rowBg = b.getBalance() != null && b.getBalance().compareTo(BigDecimal.ZERO) < 0
                    ? new BaseColor(254, 242, 242) : BaseColor.WHITE;
                addBalanceRow(balTbl, b.getClaimTitle(), fmt(b.getEntitled()),
                    fmt(b.getClaimed()), fmt(b.getBalance()), fTD, rowBg);
            }
            doc.add(balTbl);
        }

        doc.add(Chunk.NEWLINE);
        PdfPTable sigTbl = new PdfPTable(3);
        sigTbl.setWidthPercentage(100);
        for (String lbl : new String[]{"Submitted by:", "Verified by:", "Approved by:"}) {
            PdfPCell c = new PdfPCell();
            c.setBorder(Rectangle.NO_BORDER); c.setBorderWidthBottom(1f);
            c.setBorderColorBottom(BaseColor.LIGHT_GRAY);
            c.setPaddingBottom(28); c.setPaddingTop(6);
            c.setPhrase(new Phrase(lbl, fLabel));
            sigTbl.addCell(c);
        }
        doc.add(sigTbl);

        if (form.getWfStatus() != null && !"FINANCE_PROCESSED".equals(form.getWfStatus())) {
            addWatermark(writer, form.getWfStatus().replace("_", " "));
        }

        doc.close();
        return out.toByteArray();
    }

    // ================================================================
    // IMAGE TO PDF
    // ================================================================

    private byte[] imageToPdf(Path imagePath) throws Exception {
        try (PDDocument imgDoc = new PDDocument()) {
            PDPage page = new PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
            imgDoc.addPage(page);
            PDImageXObject pdImage = PDImageXObject.createFromFile(imagePath.toString(), imgDoc);
            float pageW = page.getMediaBox().getWidth();
            float pageH = page.getMediaBox().getHeight();
            float imgW  = pdImage.getWidth();
            float imgH  = pdImage.getHeight();
            float scale = Math.min((pageW - 60) / imgW, (pageH - 60) / imgH);
            float drawW = imgW * scale;
            float drawH = imgH * scale;
            float x = (pageW - drawW) / 2;
            float y = (pageH - drawH) / 2;
            try (PDPageContentStream cs = new PDPageContentStream(imgDoc, page)) {
                cs.drawImage(pdImage, x, y, drawW, drawH);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            imgDoc.save(out);
            return out.toByteArray();
        }
    }

    // ================================================================
    // PDF HELPERS — all unchanged
    // ================================================================

    private void addClaimSection(Document doc, String title, String[] headers, float[] widths,
                                  List<ClaimRowDto> rows, String sectionType,
                                  com.itextpdf.text.Font fHead, com.itextpdf.text.Font fTH,
                                  com.itextpdf.text.Font fTD) throws DocumentException {
        doc.add(sectionTitle(title, fHead));
        PdfPTable t = new PdfPTable(headers.length);
        t.setWidthPercentage(100); t.setWidths(widths); t.setSpacingAfter(8);
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, fTH));
            c.setBackgroundColor(new BaseColor(15, 21, 35)); c.setPadding(5); t.addCell(c);
        }
        if (rows.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("—", fTD));
            empty.setColspan(headers.length); empty.setPadding(5);
            empty.setBorderColor(BaseColor.LIGHT_GRAY); t.addCell(empty);
        } else {
            for (ClaimRowDto row : rows) {
                String dateStr = row.getDate() != null ? row.getDate().format(DATE_FMT) : "—";
                String claimTitle = getClaimTitle(row.getClaimId());
                switch (sectionType) {
                    case "meal" -> addCells(t, fTD, dateStr, claimTitle, nvl(row.getDescription()),
                        nvl(row.getTimeFrom()), nvl(row.getTimeTo()), fmt(row.getAmount()), fmt(row.getTotal()));
                    case "travel" -> {
                        String loc  = row.getTravelId() != null ? getTravelLocation(row.getTravelId()) : "—";
                        String meal = row.getMealId()   != null ? getTravelMeal(row.getMealId())       : "—";
                        addCells(t, fTD, dateStr, claimTitle, loc, meal, fmt(row.getAmount()), fmt(row.getTotal()));
                    }
                    default -> addCells(t, fTD, dateStr, claimTitle, nvl(row.getDescription()),
                        fmt(row.getAmount()), fmt(row.getTotal()));
                }
            }
        }
        BigDecimal sub = rows.stream().map(r -> safe(r.getTotal())).reduce(BigDecimal.ZERO, BigDecimal::add);
        for (int i = 0; i < headers.length - 2; i++) {
            PdfPCell blank = new PdfPCell(new Phrase("")); blank.setBorder(Rectangle.NO_BORDER); t.addCell(blank);
        }
        com.itextpdf.text.Font fSubLabel = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, BaseColor.DARK_GRAY);
        PdfPCell subLabel = new PdfPCell(new Phrase("Total", fSubLabel));
        subLabel.setBackgroundColor(new BaseColor(240, 245, 255)); subLabel.setPadding(4);
        subLabel.setHorizontalAlignment(Element.ALIGN_RIGHT); t.addCell(subLabel);
        PdfPCell subVal = new PdfPCell(new Phrase(fmt(sub), fSubLabel));
        subVal.setBackgroundColor(new BaseColor(240, 245, 255)); subVal.setPadding(4);
        subVal.setHorizontalAlignment(Element.ALIGN_RIGHT); t.addCell(subVal);
        doc.add(t);
    }

    private void addCells(PdfPTable t, com.itextpdf.text.Font f, String... vals) {
        for (int i = 0; i < vals.length; i++) {
            PdfPCell c = new PdfPCell(new Phrase(nvl(vals[i]), f));
            c.setPadding(4); c.setBorderColor(BaseColor.LIGHT_GRAY);
            if (i >= vals.length - 2) c.setHorizontalAlignment(Element.ALIGN_RIGHT);
            t.addCell(c);
        }
    }

    private void addBalanceRow(PdfPTable t, String type, String ent, String claimed,
                                String balance, com.itextpdf.text.Font f, BaseColor bg) {
        for (String v : new String[]{type, ent, claimed, balance}) {
            PdfPCell c = new PdfPCell(new Phrase(v, f));
            c.setBackgroundColor(bg); c.setPadding(4); c.setBorderColor(BaseColor.LIGHT_GRAY); t.addCell(c);
        }
        PdfPCell action = new PdfPCell(new Phrase("—", f));
        action.setBackgroundColor(bg); action.setPadding(4); t.addCell(action);
    }

    private Paragraph sectionTitle(String text, com.itextpdf.text.Font f) {
        Paragraph p = new Paragraph(text, f);
        p.setSpacingBefore(8); p.setSpacingAfter(3); return p;
    }

    private void addInfoLine(PdfPCell cell, String label, String value,
                              com.itextpdf.text.Font lf, com.itextpdf.text.Font vf) {
        cell.addElement(new Phrase(label + ": ", lf));
        cell.addElement(new Phrase(nvl(value) + "\n", vf));
    }

    private void addApprovalHeader(PdfPTable t, String text, com.itextpdf.text.Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setColspan(2); c.setBackgroundColor(new BaseColor(15, 21, 35)); c.setPadding(5); t.addCell(c);
    }

    private void addApprovalRow(PdfPTable t, String level, String name,
                                 com.itextpdf.text.Font lf, com.itextpdf.text.Font vf, boolean isHeader) {
        com.itextpdf.text.Font f = isHeader ? lf : vf;
        PdfPCell c1 = new PdfPCell(new Phrase(level, f)); c1.setPadding(4); c1.setBorderColor(BaseColor.LIGHT_GRAY);
        PdfPCell c2 = new PdfPCell(new Phrase(nvl(name), f)); c2.setPadding(4); c2.setBorderColor(BaseColor.LIGHT_GRAY);
        t.addCell(c1); t.addCell(c2);
    }

    private void addWatermark(PdfWriter writer, String text) {
        PdfContentByte canvas = writer.getDirectContentUnder();
        canvas.saveState();
        com.itextpdf.text.Font wf = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 60,
            new BaseColor(200, 200, 200, 80));
        ColumnText.showTextAligned(canvas, Element.ALIGN_CENTER,
            new Phrase(text, wf), 297f, 421f, 45f);
        canvas.restoreState();
    }

    private BaseColor getStatusColor(String status) {
        if (status == null) return BaseColor.GRAY;
        return switch (status) {
            case "DRAFT"             -> BaseColor.GRAY;
            case "SUBMITTED"         -> new BaseColor(37, 99, 235);
            case "PM_APPROVED", "SUPERIOR_APPROVED" -> new BaseColor(5, 150, 105);
            case "PM_REJECTED", "SUPERIOR_REJECTED" -> new BaseColor(220, 38, 38);
            case "FINANCE_PROCESSED" -> new BaseColor(124, 58, 237);
            default                  -> BaseColor.GRAY;
        };
    }

    private Map<String, List<ClaimRowDto>> groupRowsBySection(List<ClaimRowDto> rows) {
        Map<String, List<ClaimRowDto>> map = new LinkedHashMap<>();
        map.put("MEDICAL", new ArrayList<>()); map.put("MEAL", new ArrayList<>());
        map.put("TRAVEL",  new ArrayList<>()); map.put("OTHERS", new ArrayList<>());
        for (ClaimRowDto r : rows) {
            switch (r.getClaimId()) {
                case "CL05", "CL06", "CL07", "CL13", "CL14" -> map.get("MEDICAL").add(r);
                case "CL01", "CL02"          -> map.get("MEAL").add(r);
                case "CL12"                  -> map.get("TRAVEL").add(r);
                default                      -> map.get("OTHERS").add(r);
            }
        }
        return map;
    }

    private String getClaimTitle(String claimId) {
        return claimTypeRepository.findById(claimId).map(ClaimType::getClaimTitle).orElse(claimId);
    }
    private String getTravelLocation(String travelId) {
        return travelLocationRepository.findById(travelId).map(TravelLocation::getLocation).orElse(travelId);
    }
    private String getTravelMeal(String mealId) {
        return travelMealRepository.findById(mealId).map(TravelMeal::getDescription).orElse(mealId);
    }
    private String fmt(BigDecimal v) {
        return v != null ? v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() : "0.00";
    }
    private BigDecimal safe(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
    private String nvl(String s) { return s != null && !s.isBlank() ? s : "—"; }
}
