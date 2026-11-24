import CoreImage
import CoreImage.CIFilterBuiltins
import PromoCodeCore
import SwiftUI
import UIKit
import Vision
import VisionKit

extension View {
    /// Number pads have no return key, so give edit forms a way to close the keyboard.
    func keyboardDismissable() -> some View {
        scrollDismissesKeyboard(.interactively)
            .toolbar {
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("Done") {
                        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                    }
                }
            }
    }
}

extension Day {
    /// Noon avoids the date shifting when converting across time zones.
    var noonDate: Date { date(hour: 12, in: .current) }
    init(date: Date) { self.init(date, in: .current) }

    var mediumText: String { noonDate.formatted(date: .abbreviated, time: .omitted) }
}

extension Expiry {
    var color: Color {
        switch self {
        case .never: return .secondary
        case .expired, .today: return .red
        case .inDays(let d): return d <= 7 ? .orange : .green
        }
    }
}

struct EmptyStateView: View {
    let systemImage: String
    let title: String
    let message: String

    var body: some View {
        ContentUnavailableView(title, systemImage: systemImage, description: Text(message))
    }
}

/// Capsule toggle used for filters.
struct Chip: View {
    let title: String
    let selected: Bool
    var systemImage: String?
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                if let systemImage { Image(systemName: systemImage) }
                Text(title)
            }
            .font(.subheadline.weight(.medium))
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(selected ? Color.accentColor.opacity(0.2) : Color(.secondarySystemBackground), in: Capsule())
            .overlay(Capsule().strokeBorder(selected ? Color.accentColor : .clear))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

// MARK: - Barcodes

enum BarcodeRenderer {
    private static let context = CIContext()

    /// A scannable image, or nil if the format can't be drawn on iOS (the code is still shown as text).
    static func image(_ content: String, format: BarcodeFormat) -> UIImage? {
        if RetailBarcode.canEncode(format) {
            guard let modules = RetailBarcode.modules(content, format: format) else { return nil }
            return retail(modules)
        }
        let output: CIImage?
        switch format {
        case .QR_CODE:
            let f = CIFilter.qrCodeGenerator()
            f.message = Data(content.utf8)
            f.correctionLevel = "M"
            output = f.outputImage
        case .AZTEC:
            let f = CIFilter.aztecCodeGenerator()
            f.message = Data(content.utf8)
            output = f.outputImage
        case .PDF_417:
            let f = CIFilter.pdf417BarcodeGenerator()
            f.message = Data(content.utf8)
            output = f.outputImage
        case .CODE_128:
            let f = CIFilter.code128BarcodeGenerator()
            f.message = Data(content.utf8)
            f.quietSpace = 10
            output = f.outputImage
        default:
            output = nil
        }
        guard let output, let cg = context.createCGImage(output, from: output.extent) else { return nil }
        return UIImage(cgImage: cg)
    }

    private static func retail(_ modules: [Bool]) -> UIImage {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: modules.count, height: 60))
        return renderer.image { ctx in
            UIColor.white.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: modules.count, height: 60))
            UIColor.black.setFill()
            for (i, dark) in modules.enumerated() where dark { ctx.fill(CGRect(x: i, y: 0, width: 1, height: 60)) }
        }
    }
}

/// Barcode on a white card so it scans in dark mode too.
struct BarcodeView: View {
    let content: String
    let format: BarcodeFormat

    var body: some View {
        if let image = BarcodeRenderer.image(content, format: format) {
            Image(uiImage: image)
                .interpolation(.none) // crisp bars for scanners
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(maxWidth: format.isTwoDimensional ? 220 : .infinity, maxHeight: format.isTwoDimensional ? 220 : 120)
                .padding(16)
                .frame(maxWidth: .infinity)
                .background(Color.white, in: RoundedRectangle(cornerRadius: 12))
                .accessibilityLabel("Barcode for \(content)")
        }
    }
}

struct ScannedCode: Equatable {
    let value: String
    let format: BarcodeFormat?
}

/// Camera barcode scanner (VisionKit). Shows a message on devices without it.
struct ScannerSheet: View {
    let onScan: (ScannedCode) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Group {
                if DataScannerViewController.isSupported && DataScannerViewController.isAvailable {
                    DataScanner { code in
                        onScan(code)
                        dismiss()
                    }
                    .ignoresSafeArea()
                } else {
                    EmptyStateView(
                        systemImage: "barcode.viewfinder", title: "Scanner unavailable",
                        message: DataScannerViewController.isSupported
                            ? "Allow camera access for PromoCode in Settings to scan codes."
                            : "This device can't scan barcodes. Type the code instead.")
                }
            }
            .navigationTitle("Scan code")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } } }
        }
    }
}

private struct DataScanner: UIViewControllerRepresentable {
    let onScan: (ScannedCode) -> Void

    func makeUIViewController(context: Context) -> DataScannerViewController {
        let vc = DataScannerViewController(
            recognizedDataTypes: [.barcode()], qualityLevel: .balanced,
            recognizesMultipleItems: false, isHighFrameRateTrackingEnabled: false, isHighlightingEnabled: true)
        vc.delegate = context.coordinator
        try? vc.startScanning()
        return vc
    }

    func updateUIViewController(_ vc: DataScannerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onScan: onScan) }

    final class Coordinator: NSObject, DataScannerViewControllerDelegate {
        let onScan: (ScannedCode) -> Void
        private var done = false
        init(onScan: @escaping (ScannedCode) -> Void) { self.onScan = onScan }

        func dataScanner(_ dataScanner: DataScannerViewController, didAdd addedItems: [RecognizedItem], allItems: [RecognizedItem]) {
            guard !done else { return }
            for item in addedItems {
                if case .barcode(let barcode) = item, let value = barcode.payloadStringValue, !value.isEmpty {
                    done = true
                    dataScanner.stopScanning()
                    onScan(ScannedCode(value: value, format: Self.format(barcode.observation.symbology, value: value)))
                    return
                }
            }
        }

        static func format(_ symbology: VNBarcodeSymbology, value: String) -> BarcodeFormat? {
            switch symbology {
            case .qr, .microQR: return .QR_CODE
            case .aztec: return .AZTEC
            case .dataMatrix: return .DATA_MATRIX
            case .pdf417, .microPDF417: return .PDF_417
            case .code128: return .CODE_128
            case .code39, .code39Checksum, .code39FullASCII, .code39FullASCIIChecksum: return .CODE_39
            case .code93, .code93i: return .CODE_93
            case .codabar: return .CODABAR
            case .itf14, .i2of5, .i2of5Checksum: return .ITF
            // iOS reports UPC-A as EAN-13 with a leading zero.
            case .ean13: return value.count == 13 && value.hasPrefix("0") ? .UPC_A : .EAN_13
            case .ean8: return .EAN_8
            case .upce: return .UPC_E
            default: return nil
            }
        }
    }
}

extension BarcodeFormat {
    /// The stored code for UPC-A drops the leading zero iOS adds.
    func normalized(_ value: String) -> String {
        self == .UPC_A && value.count == 13 && value.hasPrefix("0") ? String(value.dropFirst()) : value
    }
}
