// =============================================================================
// IMPORTS

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Date;
// =============================================================================


// =============================================================================
/**
 * @file   SlidingWindowsDataLinkLayer.java
 * @author Matt Kaneb & Chase Yager
 * @date   February 2020
 *
 * A data link layer that uses start/stop tags and byte packing to frame the
 * data, and that performs error management with a parity bit. For flow control,
 * it utilizes a sliding windows protocol; damaged frames are dropped.
 */
public class SlidingWindowsDataLinkLayer extends DataLinkLayer {
// =============================================================================


 
    // =========================================================================
    /**
     * Embed a raw sequence of bytes into a framed sequence.
     *
     * @param  data The raw sequence of bytes to be framed.
     * @return A complete frame.
     */
    protected Queue<Byte> createFrame (Queue<Byte> data) {

    // ID added here so that it will be accounted for
    // when the parity is calculated
    byte idAsByte;
    if( this.id == 0)
        idAsByte =(byte) '0';
    else if ( this.id == 1)
        idAsByte =(byte) '1';
    else if ( this.id == 2)
        idAsByte =(byte) '2';
    else
        idAsByte =(byte) '3';
    data.add(idAsByte);
    System.out.println("Sending Frame # " + id);
    this.id = (this.id+1)%4;


	// Calculate the parity.
	byte parity = calculateParity(data);
	
	// Begin with the start tag.
	Queue<Byte> framingData = new LinkedList<Byte>();
	framingData.add(startTag);

	// Add each byte of original data.
        for (byte currentByte : data) {

	    // If the current data byte is itself a metadata tag, then precede
	    // it with an escape tag.
	    if ((currentByte == startTag) ||
		(currentByte == stopTag) ||
		(currentByte == escapeTag)) {

		framingData.add(escapeTag);

	    }

	    // Add the data byte itself.
	    framingData.add(currentByte);

	}

	// Add the parity byte.
	framingData.add(parity);
	
	// End with a stop tag.
	framingData.add(stopTag);

	return framingData;
	
    } // createFrame ()
    // =========================================================================


    
    // =========================================================================
    /**
     * Determine whether the received, buffered data constitutes a complete
     * frame.  If so, then remove the framing metadata and return the original
     * data.  Note that any data preceding an escaped start tag is assumed to be
     * part of a damaged frame, and is thus discarded.
     *
     * @return If the buffer contains a complete frame, the extracted, original
     * data; <code>null</code> otherwise.
     */
    protected Queue<Byte> processFrame () {

	// Search for a start tag.  Discard anything prior to it.
	boolean        startTagFound = false;
	Iterator<Byte>             i = receiveBuffer.iterator();
	while (!startTagFound && i.hasNext()) {
	    byte current = i.next();
	    if (current != startTag) {
		i.remove();
	    } else {
		startTagFound = true;
	    }
	}

	// If there is no start tag, then there is no frame.
	if (!startTagFound) {
	    return null;
	}
	
	// Try to extract data while waiting for an unescaped stop tag.
        int                       index = 1;
	LinkedList<Byte> extractedBytes = new LinkedList<Byte>();
	boolean            stopTagFound = false;
	while (!stopTagFound && i.hasNext()) {

	    // Grab the next byte.  If it is...
	    //   (a) An escape tag: Skip over it and grab what follows as
	    //                      literal data.
	    //   (b) A stop tag:    Remove all processed bytes from the buffer and
	    //                      end extraction.
	    //   (c) A start tag:   All that precedes is damaged, so remove it
	    //                      from the buffer and restart extraction.
	    //   (d) Otherwise:     Take it as literal data.
	    byte current = i.next();
            index += 1;
	    if (current == escapeTag) {
		if (i.hasNext()) {
		    current = i.next();
                    index += 1;
		    extractedBytes.add(current);
		} else {
		    // An escape was the last byte available, so this is not a
		    // complete frame.
		    return null;
		}
	    } else if (current == stopTag) {
		cleanBufferUpTo(index);
		stopTagFound = true;
	    } else if (current == startTag) {
		cleanBufferUpTo(index - 1);
                index = 1;
		extractedBytes = new LinkedList<Byte>();
	    } else {
		extractedBytes.add(current);
	    }

	}

	// If there is no stop tag, then the frame is incomplete.
	if (!stopTagFound) {
	    return null;
	}

	if (debug) {
	    System.out.println("SlidingWindowsDataLinkLayer.processFrame(): Got whole frame!");
	}

    // The last byte inside the frame is the parity.  Compare it to a
    // recalculation.
    byte receivedParity   = extractedBytes.remove(extractedBytes.size() - 1);
    byte calculatedParity = calculateParity(extractedBytes);
    if (receivedParity != calculatedParity) {
        System.out.printf("SlidingWindowsDataLinkLayer.processFrame():\tDamaged frame\n");
        return null;
    }


    // The second to last byte is the id of the frame. If it does fall within the window
    // of acceptable IDs then return null
    byte receivedID = extractedBytes.remove(extractedBytes.size() - 1);
    System.out.printf("Received ID # %c\n",receivedID);
    byte idAsByte;
    int ident;
    int receivedIDasInt = (int)Character.getNumericValue(receivedID);
    if (leadingHand<trailingHand){
        if (!( (receivedIDasInt >= trailingHand) ||
               (receivedIDasInt == leadingHand)  ||
               (receivedIDasInt == 0))){

            System.out.printf("SlidingWindowsDataLinkLayer.processFrame():\tWrong ID\n");
            System.out.println("Expecting ID between" + trailingHand + " and " + leadingHand);
            return null;
        }
    } else if (leadingHand>trailingHand){
        if (!( (receivedIDasInt >= trailingHand) ||
               (receivedIDasInt <= leadingHand))      ){

            System.out.printf("SlidingWindowsDataLinkLayer.processFrame():\tWrong ID\n");
            
            System.out.println("Expecting IDs between " + trailingHand + " and " + leadingHand);
            return null;
        }
    } else{
        if (receivedIDasInt < trailingHand){
            System.out.printf("SlidingWindowsDataLinkLayer.processFrame():\tWrong ID\n");
            System.out.println("Expecting ID # " + trailingHand);
            ident = (Character.getNumericValue(receivedID)+3)%4;
                if( ident == 0)
                    idAsByte =(byte) '0';
                else if ( ident == 1)
                    idAsByte =(byte) '1';
                else if ( ident == 2)
                    idAsByte =(byte) '2';
                else
                    idAsByte =(byte) '3';
            sendACK(idAsByte);
            System.out.printf("Re-Sending ACK # %c\n",idAsByte);
            return null;
        } else if (receivedIDasInt > trailingHand){
            System.out.println("Dropping Frame");
            return null;
        }
    }
	return extractedBytes;
    } // processFrame ()
    // =========================================================================



    // =========================================================================
    /**
     * After sending a frame, do any bookkeeping (e.g., buffer the frame in case
     * a resend is required).
     *
     * @param frame The framed data that was transmitted.
     */ 
    protected void finishFrameSend (Queue<Byte> frame) {
        Date d = new Date();
    	
        // Stores this frame in case it needs to be resent
        this.reSend.add(convertQueuetoLL(frame));

        // Grabs the timestamp of when this frame was sent 
    	this.timeSinceSent.add(d.getTime());

        // Reports that the host is waiting for acknowledgement
    	this.lookingForACKs = true;
        
    } // finishFrameSend ()
    // =========================================================================



    // =========================================================================
    /**
     * After receiving a frame, do any bookkeeping (e.g., deliver the frame to
     * the client, if appropriate) and responding (e.g., send an
     * acknowledgment).
     *
     * @param frame The frame of bytes received.
     */
    protected void finishFrameReceive (Queue<Byte> frame) {
        // In this protocol, if the host is looking for an ACK,
        // anything it recieves is an ACK, so upon recieving an ACK
        // it reports that it is no longer looking for an ACK. Note: 
        // processFrame() has already checked if this frame has 
        // the right ID
        if (this.lookingForACKs){
            this.trailingHand = (this.trailingHand+1)%4;

            this.reSend.remove();
            this.timeSinceSent.remove();
            if(this.reSend.isEmpty() && this.sendBuffer.isEmpty())
                this.lookingForACKs = false;
        } 

        // If the host is not looking for an ACK
        // the host delivers the frame to the client 
        // and sends an ACK 
        else{
	        // Deliver frame to the client.
	        byte[] deliverable = new byte[frame.size()];
	        for (int i = 0; i < deliverable.length; i += 1) {
	            deliverable[i] = frame.remove();
	        }

	        client.receive(deliverable);
            this.leadingHand = (this.leadingHand+1)%4;

	        // send ACK
            byte ack;
            if( this.id == 0)
                ack =(byte) '0';
            else if ( this.id == 1)
                ack =(byte) '1';
            else if ( this.id == 2)
                ack =(byte) '2';
            else
                ack =(byte) '3';
	        sendACK(ack);
            this.trailingHand = (this.trailingHand+1)%4;
            System.out.printf("Sending ACK # %c\n",ack);
            this.id = (this.id+1)%4;

	    }
    } // finishFrameReceive ()
    // =========================================================================


    // =========================================================================
    /**
     * Determine whether a timeout should occur and be processed.  This method
     * is called regularly in the event loop, and should check whether too much
     * time has passed since some kind of response is expected.
     */
    protected void checkTimeout () {
    	long now;
    	Date d = new Date();
        LinkedList<Byte> r;
        if(!this.reSend.isEmpty()){
        	now = d.getTime();
        	if ( now - timeSinceSent.peek() >= timeoutTime){
                timeSinceSent.remove();
                r = this.reSend.remove();
                this.reSend.addFirst(r);
        		this.timeSinceSent.addFirst(now);
                System.out.println("Re-Sent Frame");
                transmit(convertLLtoQueue(r));
            }
        }
    } // checkTimeout ()
    // =========================================================================


    // =========================================================================
    /**
     * Given an ID, this method sends an acknowledgement of the frame with that ID
     * to the other host     
     */
    protected void sendACK(byte identify){
        Queue<Byte> data = new LinkedList<Byte>();
        data.add(identify);
        byte parity = calculateParity(data);

        //Create a frame containing just the ID of the ACK
        Queue<Byte> framedACK = new LinkedList<Byte>();
        framedACK.add(startTag);
        framedACK.add(identify);
        framedACK.add(parity);
        framedACK.add(stopTag);

        // Send the ACK
        transmit(framedACK);
    } // sendACK ()
    // =========================================================================




    // =========================================================================
    /**
     * Extract the next frame-worth of data from the sending buffer, frame it,
     * and then send it.
     *
     * @return the frame of bytes transmitted.
     */
    protected Queue<Byte> sendNextFrame () {
        // Only sends frames if id is within the sliding window range
    	if(Math.abs(this.leadingHand - this.trailingHand) < 2 ){
    		this.leadingHand=(this.leadingHand+1)%4;
            return super.sendNextFrame();
        }
    	else{
            return null;
        }
    }// sendNextFrame ()
    // =========================================================================



    // =========================================================================
    /**
     * For a sequence of bytes, determine its parity.
     *
     * @param data The sequence of bytes over which to calculate.
     * @return <code>1</code> if the parity is odd; <code>0</code> if the parity
     *         is even.
     */
    private byte calculateParity (Queue<Byte> data) {

	int parity = 0;
	for (byte b : data) {
	    for (int j = 0; j < Byte.SIZE; j += 1) {
		if (((1 << j) & b) != 0) {
		    parity ^= 1;
		}
	    }
	}

	return (byte)parity;
	
    } // calculateParity ()
    // =========================================================================
    


    // =========================================================================
    /**
     * Remove a leading number of elements from the receive buffer.
     *
     * @param index The index of the position up to which the bytes are to be
     *              removed.
     */
    private void cleanBufferUpTo (int index) {

        for (int i = 0; i < index; i += 1) {
            receiveBuffer.remove();
	}

    } // cleanBufferUpTo ()
    // =========================================================================


    // =========================================================================
    /**
     * I wanted to store frames to be resent in a queue of byte queues but was 
     * unable to figure out how/ dont think you can, so I stored them in a 
     * linked list of byte linked lists and wrote this to convert the byte linked lists
     * into queues so they can be used by the transmit() method. 
     *
     * @param index The index of the position up to which the bytes are to be
     *              removed.
     */
    private Queue<Byte> convertLLtoQueue(LinkedList<Byte> frame){
        Queue<Byte> f = new LinkedList<Byte>();

        while(!frame.isEmpty()){
            f.add(frame.remove());
        }
        return f;
    }


    private LinkedList<Byte> convertQueuetoLL(Queue<Byte> frame){
        LinkedList<Byte> f = new LinkedList<Byte> ();
        while(!frame.isEmpty()){
            f.add(frame.remove());
        }
        return f;

    }

    // =========================================================================
    // DATA MEMBERS

    /** The start tag. */
    private final byte startTag  = (byte)'{';

    /** The stop tag. */
    private final byte stopTag   = (byte)'}';

    /** The escape tag. */
    private final byte escapeTag = (byte)'\\';

    /** True if host has yet to receive ACK */
    private boolean lookingForACKs = false;

    /** How long it has been since sending a frame */
    private LinkedList<Long> timeSinceSent = new LinkedList<Long>();

    /** The most recently sent frames, stored in case of resend */
    private LinkedList<LinkedList<Byte>> reSend = new LinkedList<LinkedList<Byte>>();

    /** How long in milliseconds will trigger a timeout */
    private double timeoutTime = 2000;

    /** The ID of the frame being expected or sent */
    private int id = 0;

    private int leadingHand = 0;

    private int trailingHand = 0;



    // =========================================================================


// =============================================================================
} // class SlidingWindowsDataLinkLayer
// =============================================================================