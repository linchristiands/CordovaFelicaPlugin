import Foundation
import CoreNFC
@objc(FelicaPlugin) class FelicaPlugin : CDVPlugin,NFCTagReaderSessionDelegate
{
    var session: NFCTagReaderSession?
    var nfcCallback: CDVInvokedUrlCommand?
    var callbackId:String=""
    @objc(startnfc:)
    func startnfc(command : CDVInvokedUrlCommand)
    {
         guard NFCTagReaderSession.readingAvailable else {
            print("NFC タグの読み取りに非対応のデバイス")
            return
        }
        print("COMMAND CALLBACKID")
        self.callbackId=command.callbackId
        print(self.callbackId)
        self.session = NFCTagReaderSession(pollingOption: .iso18092, delegate: self)
        self.session?.alertMessage = "Hold your iPhone near the item to learn more about it."
        self.session?.begin()
        print("nfc session began")
    }

    func tagReaderSessionDidBecomeActive(_ session: NFCTagReaderSession) {
        print("tagReaderSessionDidBecomeActive(_:)")
    }
    
    func tagReaderSession(_ session: NFCTagReaderSession, didInvalidateWithError error: Error) {
        print("errorSession")
        print(error)
        self.session = nil
    }
    
    func tagReaderSession(_ session: NFCTagReaderSession, didDetect tags: [NFCTag]) {
        print("tagReaderSession(_:didDetect:)")
        
         if tags.count > 1 {
            let retryInterval = DispatchTimeInterval.milliseconds(500)
            session.alertMessage = "More than 1 tag is detected, please remove all tags and try again."
            DispatchQueue.global().asyncAfter(deadline: .now() + retryInterval, execute: {
                session.restartPolling()
            })
            return
        }
         let tag = tags.first!
                 session.connect(to: tag) { (error) in
            if nil != error {
                session.invalidate(errorMessage: "Connection error. Please try again.")
                return
            }
            
            guard case .feliCa(let feliCaTag) = tag else {
                let retryInterval = DispatchTimeInterval.milliseconds(500)
                session.alertMessage = "A tag that is not FeliCa is detected, please try again with tag FeliCa."
                DispatchQueue.global().asyncAfter(deadline: .now() + retryInterval, execute: {
                    session.restartPolling()
                })
                return
            }
            
            print(feliCaTag)
            
            let idm = feliCaTag.currentIDm.map { String(format: "%.2hhx", $0) }.joined()
            let systemCode = Data([0xFE, 0x00])

            feliCaTag.polling(systemCode: systemCode, requestCode: .systemCode, timeSlot: .max1) { (pmm, systemCode, error) in
                let pmmNsData=pmm as NSData
                let pmmString=pmmNsData.map { String(format: "%.2hhx", $0) }.joined()
                let waonServiceCode = Data([0x68, 0x4f].reversed()) // get WaonCode
                let serviceCodeList = [waonServiceCode]
                let blocks = 1
                let blockList = (0..<blocks).map { (block) -> Data in
                    Data([0x80, UInt8(block)])
                }
                feliCaTag.readWithoutEncryption(serviceCodeList: serviceCodeList, blockList: blockList) {
                                (status1, status2, blockData, error) in
                                if let error = error {
                                    session.invalidate(errorMessage: error.localizedDescription)
                                    return
                                }
                                
                                guard status1 == 0x00, status2 == 0x00 else {
                                    print("ステータスフラグがエラーを示しています", status1, status2)
                                    session.invalidate(errorMessage: "ステータスフラグがエラーを示しています s1:\(status1), s2:\(status2)")
                                    return
                                }
                                let data = blockData.first!
                                let nsData = data as NSData
                                let waonnoByte=nsData.subdata(with: NSMakeRange(0, 8))
                                let waonno=waonnoByte.map { String(format: "%.2hhx", $0) }.joined()
                    let response=["idm":idm,"pmm":pmmString,"waonno":waonno]
                                        let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: response)
                    session.alertMessage = "読み取り成功"
                    print("IDm: \(idm)")
                    print("PMm: \(pmmString)")
                    print("WaonNo: \(waonno)")
                    self.commandDelegate.send(result, callbackId: self.callbackId)
                    self.session?.invalidate();
                    }
            }
        }
    }

    @objc(stopnfc:)
    func stopnfc(command : CDVInvokedUrlCommand)
    {
        self.session?.invalidate();
    }

    @objc(Hello:)
    func Hello(command : CDVInvokedUrlCommand)
    {
        let msg = command.arguments[0] as? String ?? ""
        let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: "Hello in swift"+msg)
        self.commandDelegate.send(result, callbackId: command.callbackId)
    }
}
