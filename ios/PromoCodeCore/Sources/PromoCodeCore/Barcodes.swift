import Foundation

/// Encoders for the retail barcodes Core Image can't draw (it handles QR,
/// Aztec, PDF417 and Code 128). Output is one Bool per module, true = black.
public enum RetailBarcode {
    private static let l = ["0001101", "0011001", "0010011", "0111101", "0100011", "0110001", "0101111", "0111011", "0110111", "0001011"]
    private static let g = ["0100111", "0110011", "0011011", "0100001", "0011101", "0111001", "0000101", "0010001", "0001001", "0010111"]
    private static let r = ["1110010", "1100110", "1101100", "1000010", "1011100", "1001110", "1010000", "1000100", "1001000", "1110100"]
    /// L/G pattern for EAN-13's first six digits, chosen by the leading digit.
    private static let parity = ["LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG", "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL"]

    public static func canEncode(_ format: BarcodeFormat) -> Bool {
        format == .EAN_13 || format == .EAN_8 || format == .UPC_A
    }

    /// Modules with 9-module quiet zones, or nil if [content] isn't valid for [format].
    public static func modules(_ content: String, format: BarcodeFormat) -> [Bool]? {
        let digits = content.compactMap { $0.wholeNumberValue }
        guard digits.count == content.count else { return nil }
        let bits: String?
        switch format {
        case .EAN_13: bits = ean13(complete(digits, length: 13))
        case .UPC_A: bits = ean13(complete(digits, length: 12).map { [0] + $0 }) // UPC-A is EAN-13 with a leading 0
        case .EAN_8: bits = ean8(complete(digits, length: 8))
        default: bits = nil
        }
        guard let bits else { return nil }
        let quiet = String(repeating: "0", count: 9)
        return (quiet + bits + quiet).map { $0 == "1" }
    }

    /// Accepts the full code (checking its check digit) or the code without it.
    static func complete(_ digits: [Int], length: Int) -> [Int]? {
        if digits.count == length - 1 { return digits + [checkDigit(digits)] }
        guard digits.count == length, checkDigit(Array(digits.dropLast())) == digits.last else { return nil }
        return digits
    }

    /// GS1 mod-10: weights 3,1,3,... from the rightmost data digit.
    public static func checkDigit(_ data: [Int]) -> Int {
        let sum = data.reversed().enumerated().reduce(0) { $0 + $1.element * ($1.offset % 2 == 0 ? 3 : 1) }
        return (10 - sum % 10) % 10
    }

    private static func ean13(_ d: [Int]?) -> String? {
        guard let d, d.count == 13 else { return nil }
        let p = Array(parity[d[0]])
        var s = "101"
        for i in 1...6 { s += p[i - 1] == "L" ? l[d[i]] : g[d[i]] }
        s += "01010"
        for i in 7...12 { s += r[d[i]] }
        return s + "101"
    }

    private static func ean8(_ d: [Int]?) -> String? {
        guard let d, d.count == 8 else { return nil }
        var s = "101"
        for i in 0..<4 { s += l[d[i]] }
        s += "01010"
        for i in 4..<8 { s += r[d[i]] }
        return s + "101"
    }
}
