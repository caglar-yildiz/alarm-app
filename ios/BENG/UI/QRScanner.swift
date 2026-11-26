import SwiftUI
import AVFoundation

/// Scans one QR code with the camera, on the device only.
struct QRScannerSheet: View {
    var failed: () -> Void = {}
    var scanned: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var unavailable = false

    var body: some View {
        NavigationStack {
            ZStack {
                Color.black.ignoresSafeArea()
                if unavailable {
                    Text(L.s("camera_failed")).foregroundStyle(.white).padding()
                } else {
                    QRCameraView(found: { code in
                        scanned(code)
                        dismiss()
                    }, failed: {
                        unavailable = true
                        failed()
                    })
                    .ignoresSafeArea()
                    VStack {
                        Spacer()
                        Text(L.s("qr_hint")).foregroundStyle(.white).padding().background(.black.opacity(0.5), in: Capsule()).padding(.bottom, 40)
                    }
                }
            }
            .navigationTitle(L.s("qr_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L.s("cancel")) { dismiss() } } }
        }
    }
}

struct QRCameraView: UIViewControllerRepresentable {
    var found: (String) -> Void
    var failed: () -> Void

    func makeUIViewController(context: Context) -> QRCameraController {
        let controller = QRCameraController()
        controller.found = found
        controller.failed = failed
        return controller
    }

    func updateUIViewController(_ controller: QRCameraController, context: Context) {}
}

final class QRCameraController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    var found: ((String) -> Void)?
    var failed: (() -> Void)?
    private let session = AVCaptureSession()
    private var preview: AVCaptureVideoPreviewLayer?
    private var done = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: configure()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.async { granted ? self.configure() : self.failed?() }
            }
        default: failed?()
        }
    }

    private func configure() {
        guard let device = AVCaptureDevice.default(for: .video), let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input) else {
            failed?()
            return
        }
        session.addInput(input)
        let output = AVCaptureMetadataOutput()
        guard session.canAddOutput(output) else {
            failed?()
            return
        }
        session.addOutput(output)
        output.setMetadataObjectsDelegate(self, queue: .main)
        output.metadataObjectTypes = [.qr]
        let layer = AVCaptureVideoPreviewLayer(session: session)
        layer.videoGravity = .resizeAspectFill
        layer.frame = view.bounds
        view.layer.addSublayer(layer)
        preview = layer
        DispatchQueue.global(qos: .userInitiated).async { self.session.startRunning() }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        preview?.frame = view.bounds
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        if session.isRunning { DispatchQueue.global(qos: .userInitiated).async { self.session.stopRunning() } }
    }

    func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
        guard !done, let code = (metadataObjects.first as? AVMetadataMachineReadableCodeObject)?.stringValue, !code.isEmpty else { return }
        done = true
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        found?(code)
    }
}
