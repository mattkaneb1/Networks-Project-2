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
 * @file   PARDataLinkLayer.java
 * @author Matt Kaneb & Chase Yager
 * @date   February 2020
 *
 * A data link layer that uses start/stop tags and byte packing to frame the
 * data, and that performs error management with a parity bit. For flow control,
 * it utilizes a stop & wait protocol; damaged frames are dropped.
 */
public class PARDataLinkLayer extends DataLinkLayer {
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
    data.add(id);

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
	    System.out.println("PARDataLinkLayer.processFrame(): Got whole frame!");
	}

    // The last byte inside the frame is the parity.  Compare it to a
    // recalculation.
    byte receivedParity   = extractedBytes.remove(extractedBytes.size() - 1);
    byte calculatedParity = calculateParity(extractedBytes);
    if (receivedParity != calculatedParity) {
        System.out.printf("PARDataLinkLayer.processFrame():\tDamaged frame\n");
        return null;
    }

    // The second to last byte is the id of the frame. If it does not match the id the
    // that is expected return null
    byte receivedID = extractedBytes.remove(extractedBytes.size() - 1);
    if (receivedID != this.id){
        System.out.printf("PARDataLinkLayer.processFrame():\tWrong ID\n");
        
        sendACK(receivedID);
        System.out.println("ACK Re-Sent");
        return null;
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
        this.reSend = frame;

        // Grabs the timestamp of when this frame was sent 
    	this.timeSinceSent = d.getTime();

        // Reports that the host is waiting for acknowledgement
    	this.lookingForACK = true;
        
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
        if (this.lookingForACK){
            System.out.println("ACK Recieved");
        	this.lookingForACK =false;
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

	        // send ACK
	        sendACK(this.id);
            System.out.println("ACK Sent");
	    }
        if(this.id == (byte)'0')
            this.id = (byte) '1';
        else
            this.id = (byte) '0';

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
        if(lookingForACK){
        	now = d.getTime();
        	if ( now - timeSinceSent >= timeoutTime){
        		transmit(reSend);
        		this.timeSinceSent = now;
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
    }




    // =========================================================================
    /**
     * Extract the next frame-worth of data from the sending buffer, frame it,
     * and then send it.
     *
     * @return the frame of bytes transmitted.
     */
    protected Queue<Byte> sendNextFrame () {
        // Only sends frames if ACK has 
        // been received for the last frame 
    	if (lookingForACK)
    		return null;
    	else
    		return super.sendNextFrame();
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
    // DATA MEMBERS

    /** The start tag. */
    private final byte startTag  = (byte)'{';

    /** The stop tag. */
    private final byte stopTag   = (byte)'}';

    /** The escape tag. */
    private final byte escapeTag = (byte)'\\';

    /** True if host has yet to receive ACK */
    private boolean lookingForACK = false;

    /** How long it has been since sending a frame */
    private long timeSinceSent;

    /** The most recently sent frame, stored in case of resend */
    private Queue<Byte> reSend;

    /** How long in milliseconds will trigger a timeout */
    private double timeoutTime = 2000;

    /** The ID of the frame being expected or sent */
    private byte id = (byte) '0';

    // =========================================================================


// =============================================================================
} // class PARDataLinkLayer
// =============================================================================