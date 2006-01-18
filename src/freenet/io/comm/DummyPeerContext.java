package freenet.io.comm;

/**
 * @author amphibian
 * 
 * Default PeerContext if we don't have a LowLevelFilter installed.
 * Just carries the Peer.
 */
public class DummyPeerContext implements PeerContext {

    private final Peer peer;
    
    public Peer getDetectedPeer() {
        return peer;
    }
    
    DummyPeerContext(Peer p) {
        peer = p;
    }

	public void forceDisconnect() {
		// Do nothing
	}

	public boolean isConnected() {
		return false;
	}

}
