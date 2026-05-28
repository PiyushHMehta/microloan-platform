package in.zeta.zea_2026_b02_piyushm_microloan.notification;

import in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.KfsResponse;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.loan.RepaymentScheduleEntry;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Generates a KFS (Key Fact Statement) PDF for loan disbursement notification emails.
 * Supports multi-page output when the repayment schedule overflows a single A4 page.
 */
@Component
public class KfsPdfGenerator {

    private static final float MARGIN       = 50f;
    private static final float LINE_HEIGHT  = 18f;
    private static final float A4_HEIGHT    = PDRectangle.A4.getHeight();
    private static final float A4_WIDTH     = PDRectangle.A4.getWidth();

    // Brand colours (RGB 0-1)
    private static final float[] BRAND_DARK  = {0.067f, 0.267f, 0.600f};  // #113399
    private static final float[] BRAND_LIGHT = {0.894f, 0.929f, 0.980f};  // #E4EDF9
    private static final float[] GREY_ROW    = {0.949f, 0.949f, 0.949f};  // #F2F2F2
    private static final float[] GREY_LINE   = {0.800f, 0.800f, 0.800f};  // #CCCCCC
    private static final float[] WHITE       = {1f, 1f, 1f};

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final NumberFormat      INR_FMT;
    static {
        INR_FMT = NumberFormat.getNumberInstance(new Locale("en", "IN"));
        INR_FMT.setMinimumFractionDigits(2);
        INR_FMT.setMaximumFractionDigits(2);
    }

    public byte[] generate(KfsResponse kfs) throws IOException {
        try (PDDocument document = new PDDocument()) {
            Writer w = new Writer(document);

            // ── Header bar ───────────────────────────────────────────────────
            w.fillRect(0, A4_HEIGHT - 60, A4_WIDTH, 60, BRAND_DARK);

            // Logo / brand name
            w.textAt("MICROLOAN", MARGIN, A4_HEIGHT - 22, PDType1Font.HELVETICA_BOLD, 16, WHITE);
            w.textAt("Key Fact Statement", MARGIN, A4_HEIGHT - 42, PDType1Font.HELVETICA, 10, WHITE);

            // Status badge (right-aligned)
            String badge = "PENDING ACCEPTANCE".equals(kfs.getStatus()) ? "PENDING ACCEPTANCE" : "ACCEPTED";
            float[] badgeColor = "ACCEPTED".equals(badge)
                    ? new float[]{0.133f, 0.545f, 0.133f}   // green
                    : new float[]{0.800f, 0.533f, 0.000f};  // amber
            float badgeX = A4_WIDTH - MARGIN - 130;
            w.fillRect(badgeX, A4_HEIGHT - 50, 130, 22, badgeColor);
            w.textAt(badge, badgeX + 6, A4_HEIGHT - 34, PDType1Font.HELVETICA_BOLD, 8, WHITE);

            // Generated date (right side, below badge)
            String genDate = "Generated: " + LocalDate.now().format(DATE_FMT);
            w.textAt(genDate, A4_WIDTH - MARGIN - 130, A4_HEIGHT - 58,
                    PDType1Font.HELVETICA_OBLIQUE, 8, WHITE);

            w.moveY(A4_HEIGHT - 72);

            // ── Borrower Details section ──────────────────────────────────────
            w.sectionHeader("BORROWER DETAILS");
            String nameVal = kfs.getBorrowerName() != null ? kfs.getBorrowerName() : "N/A";
            String idVal   = kfs.getLoanId() != null
                    ? kfs.getLoanId().toString().substring(0, 8).toUpperCase() + "..."
                    : "N/A";
            w.twoColumnField("Name", nameVal, "Loan ID", kfs.getLoanId() != null ? kfs.getLoanId().toString() : "N/A");
            w.gap(10);

            // ── Loan Summary section ──────────────────────────────────────────
            w.sectionHeader("LOAN SUMMARY");
            w.twoColumnField("Principal",       inr(kfs.getPrincipal()),
                             "Interest Rate",   kfs.getInterestRate() + "% p.a.");
            w.twoColumnField("Total Interest",  inr(kfs.getTotalInterest()),
                             "Total Payable",   inr(kfs.getTotalAmount()));
            w.twoColumnField("EMI Amount",      inr(kfs.getEpiAmount()),
                             "Tenure",          kfs.getTenureMonths() + " months");
            w.twoColumnField("Frequency",       kfs.getRepaymentFrequency().name(),
                             "Penalty Rate",    kfs.getPenaltyRate() + "%");
            w.twoColumnField("Installments",    String.valueOf(kfs.getNumInstallments()),
                             "", "");
            w.gap(14);

            // ── Repayment Schedule section ────────────────────────────────────
            w.sectionHeader("REPAYMENT SCHEDULE");
            w.gap(4);
            w.tableHeader();

            List<RepaymentScheduleEntry> schedule = kfs.getRepaymentSchedule();
            java.math.BigDecimal totalScheduled = java.math.BigDecimal.ZERO;
            if (schedule != null) {
                for (int i = 0; i < schedule.size(); i++) {
                    RepaymentScheduleEntry e = schedule.get(i);
                    boolean shade = (i % 2 == 0);
                    w.tableRow(e.getInstallmentNo(), e.getDueDate(), e.getEpiAmount(), shade);
                    totalScheduled = totalScheduled.add(e.getEpiAmount());
                }
                w.tableTotalRow(totalScheduled);
            }
            w.gap(20);

            // ── Acknowledgement block ─────────────────────────────────────────
            w.fillRect(MARGIN, w.getY() - 60, A4_WIDTH - 2 * MARGIN, 60, BRAND_LIGHT);
            w.moveY(w.getY() - 10);
            w.textAt("I/We acknowledge receipt and understanding of this Key Fact Statement.",
                    MARGIN + 8, w.getY(), PDType1Font.HELVETICA, 9,
                    new float[]{0.2f, 0.2f, 0.2f});
            w.moveY(w.getY() - 18);
            w.textAt("Signature: _______________________________",
                    MARGIN + 8, w.getY(), PDType1Font.HELVETICA, 9,
                    new float[]{0.2f, 0.2f, 0.2f});
            w.textAt("Date: ________________",
                    MARGIN + 8 + 260, w.getY(), PDType1Font.HELVETICA, 9,
                    new float[]{0.2f, 0.2f, 0.2f});
            w.moveY(w.getY() - 25);

            // ── Footer on every page ──────────────────────────────────────────
            w.renderFooters();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    // ── Formatting helpers ───────────────────────────────────────────────────

    private static String inr(java.math.BigDecimal v) {
        if (v == null) return "N/A";
        return "Rs. " + INR_FMT.format(v);
    }

    // ── Inner Writer ─────────────────────────────────────────────────────────

    private static class Writer {

        private final PDDocument doc;
        private final java.util.List<PDPage> pages = new java.util.ArrayList<>();
        private PDPageContentStream cs;
        private float y;
        private int pageNum = 0;

        Writer(PDDocument doc) throws IOException {
            this.doc = doc;
            newPage();
        }

        float getY() { return y; }
        void  moveY(float newY) { y = newY; }

        // ── Page management ───────────────────────────────────────────────────

        void newPage() throws IOException {
            if (cs != null) cs.close();
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            pages.add(page);
            pageNum++;
            cs = new PDPageContentStream(doc, page);
            y = A4_HEIGHT - MARGIN;
        }

        void ensureSpace(float needed) throws IOException {
            // Leave 60pt at the bottom for footer
            if (y - needed < MARGIN + 50) newPage();
        }

        // ── Low-level drawing ──────────────────────────────────────────────────

        void fillRect(float x, float fy, float w, float h, float[] rgb) throws IOException {
            cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
            cs.addRect(x, fy, w, h);
            cs.fill();
            cs.setNonStrokingColor(0, 0, 0); // reset to black
        }

        void strokeRect(float x, float fy, float w, float h, float[] rgb) throws IOException {
            cs.setStrokingColor(rgb[0], rgb[1], rgb[2]);
            cs.setLineWidth(0.5f);
            cs.addRect(x, fy, w, h);
            cs.stroke();
            cs.setStrokingColor(0, 0, 0);
        }

        void hLine(float x1, float x2, float fy, float[] rgb) throws IOException {
            cs.setStrokingColor(rgb[0], rgb[1], rgb[2]);
            cs.setLineWidth(0.5f);
            cs.moveTo(x1, fy);
            cs.lineTo(x2, fy);
            cs.stroke();
            cs.setStrokingColor(0, 0, 0);
        }

        void textAt(String text, float x, float fy, PDType1Font font, float size, float[] rgb)
                throws IOException {
            if (text == null || text.isBlank()) return;
            cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
            cs.beginText();
            cs.setFont(font, size);
            cs.newLineAtOffset(x, fy);
            cs.showText(text);
            cs.endText();
            cs.setNonStrokingColor(0, 0, 0);
        }

        // ── Section header ─────────────────────────────────────────────────────

        void sectionHeader(String title) throws IOException {
            ensureSpace(LINE_HEIGHT + 6);
            fillRect(MARGIN, y - 2, A4_WIDTH - 2 * MARGIN, LINE_HEIGHT + 2, BRAND_LIGHT);
            textAt(title, MARGIN + 4, y + 2, PDType1Font.HELVETICA_BOLD, 10, BRAND_DARK);
            y -= LINE_HEIGHT + 6;
        }

        // ── Two-column field row ───────────────────────────────────────────────

        void twoColumnField(String lbl1, String val1, String lbl2, String val2) throws IOException {
            ensureSpace(LINE_HEIGHT);
            float midX = MARGIN + (A4_WIDTH - 2 * MARGIN) / 2f;

            // col-1
            textAt(lbl1, MARGIN, y, PDType1Font.HELVETICA_BOLD, 9, new float[]{0.3f, 0.3f, 0.3f});
            textAt(val1, MARGIN + 90, y, PDType1Font.HELVETICA, 10, new float[]{0, 0, 0});

            // col-2
            if (lbl2 != null && !lbl2.isBlank()) {
                textAt(lbl2, midX, y, PDType1Font.HELVETICA_BOLD, 9, new float[]{0.3f, 0.3f, 0.3f});
                textAt(val2, midX + 90, y, PDType1Font.HELVETICA, 10, new float[]{0, 0, 0});
            }

            y -= LINE_HEIGHT;
        }

        // ── Repayment schedule table ───────────────────────────────────────────

        private static final float COL_NO    = MARGIN;
        private static final float COL_DATE  = MARGIN + 60;
        private static final float COL_AMT   = MARGIN + 220;
        private static final float TABLE_W   = 300f;

        void tableHeader() throws IOException {
            ensureSpace(LINE_HEIGHT + 2);
            fillRect(COL_NO, y - 2, TABLE_W, LINE_HEIGHT + 2, BRAND_DARK);
            textAt("#",          COL_NO + 4,   y + 2, PDType1Font.HELVETICA_BOLD, 10, WHITE);
            textAt("Due Date",   COL_DATE + 4, y + 2, PDType1Font.HELVETICA_BOLD, 10, WHITE);
            textAt("Amount (Rs.)", COL_AMT + 4,  y + 2, PDType1Font.HELVETICA_BOLD, 10, WHITE);
            y -= LINE_HEIGHT + 2;
        }

        void tableRow(int no, LocalDate date, java.math.BigDecimal amt, boolean shade)
                throws IOException {
            ensureSpace(LINE_HEIGHT);
            if (shade) fillRect(COL_NO, y - 2, TABLE_W, LINE_HEIGHT, GREY_ROW);
            hLine(COL_NO, COL_NO + TABLE_W, y - 2, GREY_LINE);
            textAt(String.valueOf(no),             COL_NO + 4,   y, PDType1Font.HELVETICA, 10, new float[]{0,0,0});
            textAt(date.format(DATE_FMT),          COL_DATE + 4, y, PDType1Font.HELVETICA, 10, new float[]{0,0,0});
            textAt(INR_FMT.format(amt),            COL_AMT + 4,  y, PDType1Font.HELVETICA, 10, new float[]{0,0,0});
            y -= LINE_HEIGHT;
        }

        void tableTotalRow(java.math.BigDecimal total) throws IOException {
            ensureSpace(LINE_HEIGHT + 2);
            fillRect(COL_NO, y - 2, TABLE_W, LINE_HEIGHT + 2, BRAND_LIGHT);
            textAt("Total", COL_DATE + 4, y + 2, PDType1Font.HELVETICA_BOLD, 10, BRAND_DARK);
            textAt(INR_FMT.format(total), COL_AMT + 4, y + 2, PDType1Font.HELVETICA_BOLD, 10, BRAND_DARK);
            y -= LINE_HEIGHT + 2;
        }

        void gap(float pts) { y -= pts; }

        // ── Footers (called once at end — draws on all pages) ─────────────────

        void renderFooters() throws IOException {
            // Close current stream first
            if (cs != null) { cs.close(); cs = null; }

            int total = pages.size();
            for (int i = 0; i < total; i++) {
                PDPage page = pages.get(i);
                try (PDPageContentStream footer = new PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, false)) {

                    // thin separator line
                    footer.setStrokingColor(GREY_LINE[0], GREY_LINE[1], GREY_LINE[2]);
                    footer.setLineWidth(0.5f);
                    footer.moveTo(MARGIN, 40);
                    footer.lineTo(A4_WIDTH - MARGIN, 40);
                    footer.stroke();

                    // left: confidentiality
                    footer.setNonStrokingColor(0.5f, 0.5f, 0.5f);
                    footer.beginText();
                    footer.setFont(PDType1Font.HELVETICA_OBLIQUE, 7);
                    footer.newLineAtOffset(MARGIN, 28);
                    footer.showText("Confidential - MicroLoan");
                    footer.endText();

                    // right: page number
                    String pageLabel = "Page " + (i + 1) + " of " + total;
                    footer.beginText();
                    footer.setFont(PDType1Font.HELVETICA, 7);
                    float pw = pageLabel.length() * 3.8f; // approx width
                    footer.newLineAtOffset(A4_WIDTH - MARGIN - pw, 28);
                    footer.showText(pageLabel);
                    footer.endText();

                    footer.setNonStrokingColor(0, 0, 0);
                }
            }
        }
    }
}
