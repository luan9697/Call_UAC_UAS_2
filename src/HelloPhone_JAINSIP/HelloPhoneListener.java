/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package HelloPhone_JAINSIP;

import com.sun.jmx.snmp.BerDecoder;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.ListeningPoint;
import javax.sip.ObjectInUseException;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipFactory;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.URI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import javax.swing.JOptionPane;

/**
 *
 * @author KhangDang
 */
public class HelloPhoneListener implements SipListener {

    private SipFactory sipFactory;
    private SipStack sipStack;
    private ListeningPoint listeningPoint;
    private SipProvider sipProvider;
    private MessageFactory messageFactory;
    private HeaderFactory headerFactory;
    private AddressFactory addressFactory;
    private ContactHeader contactHeader;

    private ClientTransaction clientTransaction;
    private ServerTransaction serverTransaction;

    private boolean isUAS = false;
    private boolean isACK = false;

    private String sIP;
    private int iSipPort;
    private HelloPhoneGUI GUI;

    // sdpOffer : táº¡o SDP message tá»« UAC vÃ  lÆ°u thÃ´ng tin
    // trong SDP message nháº­n Ä‘Æ°á»£c tá»« UAS.
    private SdpTool sdpOffer;
    // sdpOffer : táº¡o SDP message tá»« UAS vÃ  lÆ°u thÃ´ng tin
    // trong SDP message nháº­n Ä‘Æ°á»£c tá»« UAC.
    private SdpTool sdpAnswer;

    // ringClient thá»±c hiá»‡n nháº¡c chuÃ´ng phÃ­a UAC.
    RingTool ringClient;
    // ringServer thá»±c hiá»‡n nháº¡c chuÃ´ng phÃ­a UAS.
    RingTool ringServer;
    // timerS Ä‘Æ°á»£c sá»­ dá»¥ng  Ä‘á»ƒ tá»± Ä‘á»™ng
    // tá»« chá»‘i cuá»™c gá»�i tá»« UAS khi quÃ¡ 30s
    // mÃ  UAS khÃ´ng cÃ³ báº¥t cá»© hÃ nh Ä‘á»™ng nÃ o
    Timer timerS;

    // voiceClient truyá»�n media tá»« UAC -> UAS
    VoiceTool voiceClient;
    // voiceServer truyá»�n media tá»« UAS -> UAC
    VoiceTool voiceServer;

    public HelloPhoneListener(HelloPhoneGUI gui) {
        try {
            GUI = gui;
            sIP = "192.168.1.90";
            iSipPort = GUI.getSipPort();

            sipFactory = SipFactory.getInstance();
            sipFactory.setPathName("gov.nist");

            Properties properties = new Properties();
            properties.setProperty("javax.sip.STACK_NAME", "myStack");
            sipStack = sipFactory.createSipStack(properties);

            messageFactory = sipFactory.createMessageFactory();
            headerFactory = sipFactory.createHeaderFactory();
            addressFactory = sipFactory.createAddressFactory();

            listeningPoint = sipStack.createListeningPoint(sIP, iSipPort, "udp");
            sipProvider = sipStack.createSipProvider(listeningPoint);
            sipProvider.addSipListener(this);

            Address contactAddress = addressFactory.createAddress("sip:" + sIP + ":" + iSipPort);
            contactHeader = headerFactory.createContactHeader(contactAddress);

            // khá»Ÿi táº¡o sdpOffer vÃ  sdpAnswer
            sdpOffer = new SdpTool();
            sdpAnswer = new SdpTool();

            // khá»Ÿi táº¡o ringClient vÃ  ringServer
            ringClient = new RingTool();
            ringServer = new RingTool();

            // khá»Ÿi táº¡o voiceClient vÃ  voiceServer
            voiceClient = new VoiceTool();
            voiceServer = new VoiceTool();

            GUI.setInit("Init : " + sIP + ":" + iSipPort);

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    public boolean isUAS() {
        return isUAS;
    }

    public void disconnect() {
        try {
            sipProvider.removeSipListener(this);
            sipProvider.removeListeningPoint(listeningPoint);
            sipStack.deleteListeningPoint(listeningPoint);
            sipStack.deleteSipProvider(sipProvider);

            GUI.clean();

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    public void sendRequest() {
        try {
            Address toAddress = addressFactory.createAddress(GUI.getDestination());
            ToHeader toHeader = headerFactory.createToHeader(toAddress, null);

            Address fromAddress = addressFactory.createAddress("sip:" + sIP + ":" + iSipPort);
            FromHeader fromHeader = headerFactory.createFromHeader(fromAddress, "564385");

            ViaHeader viaHeader = headerFactory.createViaHeader(sIP, iSipPort, "udp", null);
            ArrayList viaHeaders = new ArrayList();
            viaHeaders.add(viaHeader);

            MaxForwardsHeader maxForwardsHeader = headerFactory.createMaxForwardsHeader(20);

            CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, "INVITE");

            CallIdHeader callIdHeader = sipProvider.getNewCallId();

            URI requestUri = toAddress.getURI();
            Request request = messageFactory.createRequest(requestUri, "INVITE",
                    callIdHeader, cSeqHeader, fromHeader, toHeader,
                    viaHeaders, maxForwardsHeader);
            request.addHeader(contactHeader);

            // senderInfo_UAC : chá»©a cÃ¡c thÃ´ng tin Ä‘á»ƒ thá»±c hiá»‡n voice chat cá»§a UAC
            SdpInfo senderInfo_UAC = new SdpInfo();
            senderInfo_UAC.setIpSender(sIP);
            senderInfo_UAC.setVoicePort(GUI.getVoicePort());
            senderInfo_UAC.setVoiceFormat(0);

            // Ä�á»‹nh nghÄ©a loáº¡i ná»™i dung dÃ nh cho message body cá»§a INVITE request
            // chÃºng ta sá»­ dá»¥ng application/sdp.
            ContentTypeHeader myContentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");
            // táº¡o SDP message vÃ  lÆ°u cÃ¡c thÃ´ng tin vÃ o biáº¿n senderInfo cá»§a sdpOffer
            byte[] content = sdpOffer.createSdp(senderInfo_UAC);
            // lÆ°u SDP message vÃ o message body cá»§a INVITE 
            request.setContent(content, myContentTypeHeader);

            clientTransaction = sipProvider.getNewClientTransaction(request);
            clientTransaction.sendRequest();

            // play nháº¡c chuÃ´ng phÃ­a UAC
            ringClient.playRing("file://C:\\Users\\15510\\Downloads\\ringclient.mp2");

            GUI.Display("Send : " + request.toString());

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    public void terminateRequest() {
        try {
            if (!isACK) {
                // UAC táº¡o vÃ  gá»Ÿi CANCEL request Ä‘á»ƒ há»§y cuá»™c gá»�i
                Request cancelRequest = clientTransaction.createCancel();
                // táº¡o 1 ClientTransaction má»›i dÃ nh cho CANCEL request
                ClientTransaction cancelClientTransaction
                        = sipProvider.getNewClientTransaction(cancelRequest);
                cancelClientTransaction.sendRequest();

                // UAC há»§y cuá»™c gá»�i, dá»«ng nháº¡c chuÃ´ng
                ringClient.stopRing();

                GUI.Display("Send : " + cancelRequest.toString());

            } else {
                // UAC táº¡o BYE request Ä‘á»ƒ káº¿t thÃºc cuá»™c gá»�i
                Request byeRequest
                        = clientTransaction.getDialog().createRequest(Request.BYE);
                // bá»• sung contact header vÃ o BYE
                byeRequest.addHeader(contactHeader);
                // táº¡o 1 ClientTransaction má»›i dÃ nh cho BYE request
                ClientTransaction byeClientTransaction
                        = sipProvider.getNewClientTransaction(byeRequest);
                // Sá»­ dá»¥ng Dialog Ä‘á»ƒ gá»Ÿi BYE request
                clientTransaction.getDialog().sendRequest(byeClientTransaction);
                GUI.Display("Send : " + byeRequest.toString());

                // thiáº¿t láº­p láº¡i giÃ¡ trá»‹ isACK
                isACK = false;

                // káº¿t thÃºc voice chat phÃ­a UAC
                voiceClient.stopMedia();
            }

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    public void sendResponse() {
        try {
            // láº¥y INVITE request tá»« serverTransaction
            Request request = serverTransaction.getRequest();

            // táº¡o ra 200 OK response
            Response response = messageFactory.createResponse(200, request);
            response.addHeader(contactHeader);

            // láº¥y SDP message trong message body cá»§a INVITE
            byte[] cont = (byte[]) request.getContent();
            // láº¥y ra cÃ¡c thÃ´ng tin trong SDP message nÃ y vÃ  lÆ°u trong biáº¿n receiverInfo
            sdpAnswer.getSdp(cont);

            // senderInfo_UAS : chá»©a cÃ¡c thÃ´ng tin Ä‘á»ƒ thá»±c hiá»‡n voice chat cá»§a UAS
            SdpInfo senderInfo_UAS = new SdpInfo();
            senderInfo_UAS.setIpSender(sIP);
            senderInfo_UAS.setVoicePort(GUI.getVoicePort());
            senderInfo_UAS.setVoiceFormat(0);

            // Ä�á»‹nh nghÄ©a loáº¡i ná»™i dung dÃ nh cho message body cá»§a 200 OK response
            // chÃºng ta sá»­ dá»¥ng application/sdp.
            ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");
            // táº¡o SDP message vÃ  lÆ°u cÃ¡c thÃ´ng tin vÃ o biáº¿n senderInfo cá»§a sdpAnswer
            byte[] myContent = sdpAnswer.createSdp(senderInfo_UAS);
            // lÆ°u SDP message vÃ o message body cá»§a 200 OK response
            response.setContent(myContent, contentTypeHeader);

            // gá»Ÿi response
            serverTransaction.sendResponse(response);

            // Stop nháº¡c chuÃ´ng Ä‘á»ƒ chuáº©n bá»‹ thá»±c hiá»‡n voice chat
            ringServer.stopRing();
            // há»§y bá»� lá»‹ch trÃ¬nh
            timerS.cancel();

            // thá»±c hiá»‡n voice chat phÃ­a UAS :
            // thÃ´ng tin vá»� UAS trong sdpAnswer lÃ  senderInfo
            voiceServer.senderInfo(sdpAnswer.getSenderInfo());
            // thÃ´ng tin vá»� UAC trong sdpAnswer lÃ  receiverInfo
            voiceServer.receiverInfo(sdpAnswer.getReceiverInfo());
            // khá»Ÿi táº¡o Session
            voiceServer.init();
            // báº¯t Ä‘áº§u Session
            voiceServer.startMedia();
            // gá»Ÿi send stream
            voiceServer.send();

            GUI.Display("Send : " + response.toString());

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    public void terminateResponse() {
        try {
            if (!isACK) {
                // láº¥y INVITE request tá»« serverTransaction
                Request request = serverTransaction.getRequest();

                // táº¡o "478 Termnated" response vÃ  gá»Ÿi Ä‘áº¿n UAC
                Response response = messageFactory.createResponse(487, request);
                serverTransaction.sendResponse(response);

                GUI.Display("Send : " + response.toString());

                // UAS tá»« chá»‘i cuá»™c gá»�i, dá»«ng nháº¡c chuÃ´ng
                ringServer.stopRing();
                // há»§y bá»� lá»‹ch trÃ¬nh
                timerS.cancel();
            } else {
                // UAS táº¡o BYE request Ä‘á»ƒ káº¿t thÃºc cuá»™c gá»�i
                Request byeRequest
                        = serverTransaction.getDialog().createRequest("BYE");
                byeRequest.addHeader(contactHeader);

                // táº¡o 1 ClientTransaction dÃ nh BYE
                ClientTransaction byeclientTransaction
                        = sipProvider.getNewClientTransaction(byeRequest);
                // sá»­ dá»¥ng Dialog Ä‘á»ƒ gá»Ÿi BYE
                serverTransaction.getDialog().sendRequest(byeclientTransaction);

                GUI.Display("Send : " + byeRequest.toString());

                // thiáº¿t láº­p láº¡i giÃ¡ trá»‹ isACK
                isACK = false;

                //káº¿t thÃºc cuá»™c gá»�i phÃ­a UAS
                voiceServer.stopMedia();
            }

            // Khi káº¿t thÃºc thÃ¬ peer nÃ y khÃ´ng cÃ²n lÃ  UAS,nÃªn thiáº¿t láº­p isUAS = false
            isUAS = false;

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    @Override
    public void processRequest(RequestEvent requestEvent) {
        try {
            Request request = requestEvent.getRequest();
            GUI.Display("Received : " + request.toString());

            if (request.getMethod().equals(Request.INVITE)) {
                // thiáº¿t láº­p isUAS = true, Ä‘á»ƒ chá»©ng tá»� Ä‘Ã¢y lÃ  UAS
                isUAS = true;
                // táº¡o 180 RINGING                
                Response response = messageFactory.createResponse(180, request);
                response.addHeader(contactHeader);
                // response Ä‘áº§u tiÃªn pháº£n há»“i láº¡i tá»« 1 request,thÃ¬ pháº£i Ä‘á»‹nh nghÄ©a 
                // giÃ¡ trá»‹ tag cá»§a ToHeader, nhá»¯ng response sau nÃ y trong cÃ¹ng Dialog
                // khÃ´ng cáº§n Ä‘á»‹nh nghÄ©a láº¡i giÃ¡ trá»‹ tag cá»§a ToHeader
                ToHeader toHeader = (ToHeader) response.getHeader("To");
                toHeader.setTag("45432678");

                // táº¡o Ä‘á»‘i tÆ°á»£ng ServerTransaction má»›i dÃ nh cho INVITE request.
                serverTransaction = sipProvider.getNewServerTransaction(request);
                serverTransaction.sendResponse(response);

                // Play nháº¡c chuÃ´ng phÃ­a UAS
                ringServer.playRing("file://C:\\Users\\15510\\Downloads\\RingServer.wav");

                // Ä�á»‹nh nghÄ©a Ä‘á»‘i tÆ°á»£ng TimerTask sáº½ thá»±c hiá»‡n hÃ nh Ä‘á»™ng
                // gá»�i phÆ°Æ¡ng thá»©c terminateResponse() Ä‘á»ƒ tá»« chá»‘i cuá»™c gá»�i
                TimerTask task = new TimerTask() {
                    @Override
                    public void run() {
                        HelloPhoneListener.this.terminateResponse();
                    }
                };
                // Khá»Ÿi táº¡o Ä‘á»‘i tÆ°á»£ng timerS
                timerS = new Timer("UAS khÃ´ng pháº£n há»“i");
                // Láº­p lá»‹ch task sáº½ tá»± Ä‘á»™ng thá»±c hiá»‡n sau 30s ná»¯a
                timerS.schedule(task, 30000);

                GUI.Display("send : " + response.toString());
            }

            if (request.getMethod().equals(Request.CANCEL)) {
                // láº¥y INVITE request tá»« serverTransaction
                Request inviteReq = serverTransaction.getRequest();

                // táº¡o "487 termniated" response Ä‘á»ƒ há»§y cuá»™c gá»�i dÃ nh cho INVITE 
                Response response = messageFactory.createResponse(487, inviteReq);
                serverTransaction.sendResponse(response);
                GUI.Display("send : " + response.toString());

                // táº¡o "200 OK" response dÃ nh cho CANCEL           
                Response cancelResponse = messageFactory.createResponse(200, request);
                // táº¡o Ä‘á»‘i tÆ°á»£ng ServerTransaction dÃ nh cho CANCEL request
                ServerTransaction cancelServerTransaction = requestEvent.getServerTransaction();
                cancelServerTransaction.sendResponse(cancelResponse);
                GUI.Display("send : " + cancelResponse.toString());

                // UAC há»§y cuá»™c gá»�i, dá»«ng nháº¡c chuÃ´ng
                ringServer.stopRing();
                JOptionPane.showMessageDialog(GUI, "UAC Ä‘Ã£ há»§y cuá»™c gá»�i !");
                //há»§y bá»� lá»‹ch trÃ¬nh
                timerS.cancel();

                // peer nÃ y khÃ´ng cÃ²n lÃ  UAS
                isUAS = false;
            }

            if (request.getMethod().equals(Request.ACK)) {
                // isACK = true, chá»©ng tá»� UAC Ä‘Ã£ gá»Ÿi ACK request
                isACK = true;
            }

            if (request.getMethod().equals(Request.BYE)) {
                // táº¡o "200 OK" response dÃ nh cho BYE request
                Response response = messageFactory.createResponse(200, request);
                response.addHeader(contactHeader);
                // táº¡o Ä‘á»‘i tÆ°á»£ng ServerTransaction dÃ nh cho BYE request
                ServerTransaction byeServerTransaction = requestEvent.getServerTransaction();
                byeServerTransaction.sendResponse(response);

                if (!isUAS) {
                    voiceClient.stopMedia();
                    JOptionPane.showMessageDialog(GUI, "UAS Ä‘Ã£ káº¿t thÃºc cuá»™c gá»�i !");
                } 
                
                if(isUAS){
                    voiceServer.stopMedia();
                    JOptionPane.showMessageDialog(GUI, "UAC Ä‘Ã£ káº¿t thÃºc cuá»™c gá»�i !");
                }

                GUI.Display("Send : " + response.toString());
                // peer nÃ y khÃ´ng cÃ²n lÃ  UAS
                isUAS = false;
                // thiáº¿t láº­p láº¡i giÃ¡ trá»‹ isACK
                isACK = false;
            }

        } catch (Exception ex) {
            System.out.println("processRequest : " + ex.getMessage());
        }
    }

    @Override
    public void processResponse(ResponseEvent responseEvent ) {
        try {
            Response response = responseEvent.getResponse();
            GUI.Display("Received : " + response.toString());
            CSeqHeader cSeqHeader = (CSeqHeader) response.getHeader(CSeqHeader.NAME);

            if ((cSeqHeader.getMethod().equals(Request.INVITE))
                    && (response.getStatusCode() == 200)) {

                // láº¥y SDP message trong message body cá»§a 200 OK
                byte[] content = (byte[]) response.getContent();
                // láº¥y ra cÃ¡c thÃ´ng tin trong SDP message nÃ y vÃ  lÆ°u trong biáº¿n receiverInfo
                sdpOffer.getSdp(content);

                // Sá»­ dá»¥ng Dialog Ä‘á»ƒ táº¡o vÃ  gá»Ÿi ACK request
                long numseq = cSeqHeader.getSeqNumber();
                Request ACK = clientTransaction.getDialog().createAck(numseq);
                ACK.addHeader(contactHeader);
                clientTransaction.getDialog().sendAck(ACK);

                // stop nháº¡c chuÃ´ng Ä‘á»ƒ báº¯t Ä‘áº§u thá»±c hiá»‡n voice chat
                ringClient.stopRing();

                // isACK = true, chá»©ng tá»� UAC Ä‘Ã£ gá»Ÿi ACK
                isACK = true;

                // thá»±c hiá»‡n voice chat phÃ­a UAC :
                // thÃ´ng tin vá»� UAC trong sdpOffer lÃ  senderInfo
                voiceClient.senderInfo(sdpOffer.getSenderInfo());
                // thÃ´ng tin vá»� UAS trong sdpOffer lÃ  receiverInfo
                voiceClient.receiverInfo(sdpOffer.getReceiverInfo());
                // khá»Ÿi táº¡o Session
                voiceClient.init();
                // báº¯t Ä‘áº§u Session
                voiceClient.startMedia();
                // gá»Ÿi send stream
                voiceClient.send();

                GUI.Display("Send : " + ACK.toString());
            }

            // Náº¿u UAC nháº­n Ä‘Æ°á»£c "487 terminated"
            if (response.getStatusCode() == 487) {
                // UAS tá»« chá»‘i cuá»™c gá»�i, dá»«ng nháº¡c chuÃ´ng
                ringClient.stopRing();
                // hiá»ƒn thá»‹ thÃ´ng bÃ¡o "UAS Ä‘Ã£ tá»« chá»‘i cuá»™c gá»�i"
                JOptionPane.showMessageDialog(GUI, "UAS Ä‘Ã£ tá»« chá»‘i cuá»™c gá»�i !");
            }

        } catch (Exception ex) {
            System.out.println("processResponse : " + ex.getMessage());
        }
    }

    @Override
    public void processTimeout(TimeoutEvent timeoutEvent
    ) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void processIOException(IOExceptionEvent exceptionEvent
    ) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent
    ) {
        // Xem cÃ¡c ClientTransaction Ä‘Ã£ káº¿t thÃºc
        ClientTransaction clientTransaction = transactionTerminatedEvent.getClientTransaction();
        //   System.out.println("ClientTrasaction Terminated : " + clientTransaction.getRequest());
    }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent
    ) {
        // Xem cÃ¡c Dialog Ä‘Ã£ káº¿t thÃºc
        Dialog dialog = dialogTerminatedEvent.getDialog();
        //   System.out.println("Dialog Terminated : " + dialog);
    }

}
