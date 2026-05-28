package in.zeta.zea_2026_b02_piyushm_microloan_notif.notification;

import in.zeta.zea_2026_b02_piyushm_microloan_notif.dto.loan.KfsResponse;
import in.zeta.zea_2026_b02_piyushm_microloan_notif.dto.loan.RepaymentScheduleEntry;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Generates a KFS (Key Fact Statement) PDF for loan disbursement notification emails.
 * Supports multi-page output when the repayment schedule overflows a single A4 page.
 */
@Component
public class KfsPdfGenerator {

    private static final float MARGIN = 50f;
    private static final float LINE_HEIGHT = 18f;
    private static final float A4_HEIGHT = PDRectangle.A4.getHeight();
    private static final float A4_WIDTH  = PDRectangle.A4.getWidth();

    public byte[] generate(KfsResponse kfs) throws IOException {
        try (PDDocument document = new PDDocument()) {
            Writer writer = new Writer(document);

            // ── Title ────────────────────────────────────────────────────────
            writer.boldLine("KEY FACT STATEMENT", 16);
            writer.divider();
            writer.gap(6);

            // ── Loan Summary ─────────────────────────────────────────────────
            writer.boldLine("Loan Summary", 13);
            writer.gap(4);
            writer.field("Loan ID",         kfs.getLoanId().toString());
            writer.field("Principal",        "Rs. " + kfs.getPrincipal());
            writer.field("Interest Rate",    kfs.getInterestRate() + "%");
            writer.field("Total Interest",   "Rs. " + kfs.getTotalInterest());
            writer.field("Total Amount",     "Rs. " + kfs.getTotalAmount());
            writer.field("EPI Amount",       "Rs. " + kfs.getEpiAmount());
            writer.field("Frequency",        kfs.getRepaymentFrequency().name());
            writer.field("Tenure",           kfs.getTenureMonths() + " months");
            writer.field("Installments",     String.valueOf(kfs.getNumInstallments()));
            writer.field("Penalty Rate",     kfs.getPenaltyRate() + "%");
            writer.gap(14);

            // ── Repayment Schedule ───────────────────────────────────────────
            writer.boldLine("Repayment Schedule", 13);
            writer.gap(4);
            writer.tableHeader("#", "Due Date", "Amount (Rs.)");

            List<RepaymentScheduleEntry> schedule = kfs.getRepaymentSchedule();
            if (schedule != null) {
                for (RepaymentScheduleEntry entry : schedule) {
                    writer.tableRow(
                            String.valueOf(entry.getInstallmentNo()),
                            entry.getDueDate().toString(),
                            entry.getEpiAmount().toPlainString()
                    );
                }
            }

            writer.gap(20);
            writer.italicLine("This is a system-generated Key Fact Statement.", 9);

            writer.close();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    // ── Inner Writer ─────────────────────────────────────────────────────────

    private static class Writer {

        private final PDDocument doc;
        private PDPageContentStream cs;
        private float y;

        Writer(PDDocument doc) throws IOException {
            this.doc = doc;
            newPage();
        }

        private void newPage() throws IOException {
            if (cs != null) {
                cs.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            y = A4_HEIGHT - MARGIN;
        }

        private void ensureSpace(float needed) throws IOException {
            if (y - needed < MARGIN) {
                newPage();
            }
        }

        void boldLine(String text, float size) throws IOException {
            ensureSpace(LINE_HEIGHT);
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA_BOLD, size);
            cs.newLineAtOffset(MARGIN, y);
            cs.showText(text);
            cs.endText();
            y -= LINE_HEIGHT;
        }

        void italicLine(String text, float size) throws IOException {
            ensureSpace(LINE_HEIGHT);
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA_OBLIQUE, size);
            cs.newLineAtOffset(MARGIN, y);
            cs.showText(text);
            cs.endText();
            y -= LINE_HEIGHT;
        }

        void field(String label, String value) throws IOException {
            ensureSpace(LINE_HEIGHT);
            String line = String.format("%-20s: %s", label, value != null ? value : "N/A");
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA, 11);
            cs.newLineAtOffset(MARGIN, y);
            cs.showText(line);
            cs.endText();
            y -= LINE_HEIGHT;
        }

        void tableHeader(String c1, String c2, String c3) throws IOException {
            ensureSpace(LINE_HEIGHT);
            drawTableRow(PDType1Font.HELVETICA_BOLD, 11, c1, c2, c3);
        }

        void tableRow(String c1, String c2, String c3) throws IOException {
            ensureSpace(LINE_HEIGHT);
            drawTableRow(PDType1Font.HELVETICA, 10, c1, c2, c3);
        }

        private void drawTableRow(PDType1Font font, float size, String c1, String c2, String c3)
                throws IOException {
            float col1X = MARGIN;
            float col2X = MARGIN + 50;
            float col3X = MARGIN + 200;

            for (float[] pair : new float[][]{{col1X, 0}, {col2X, 1}, {col3X, 2}}) {
                String[] cols = {c1, c2, c3};
                cs.beginText();
                cs.setFont(font, size);
                cs.newLineAtOffset(pair[0], y);
                cs.showText(cols[(int) pair[1]] != null ? cols[(int) pair[1]] : "");
                cs.endText();
            }
            y -= LINE_HEIGHT;
        }

        void divider() throws IOException {
            cs.setLineWidth(1f);
            cs.moveTo(MARGIN, y);
            cs.lineTo(A4_WIDTH - MARGIN, y);
            cs.stroke();
            y -= 10;
        }

        void gap(float pts) {
            y -= pts;
        }

        void close() throws IOException {
            if (cs != null) {
                cs.close();
            }
        }
    }
}
