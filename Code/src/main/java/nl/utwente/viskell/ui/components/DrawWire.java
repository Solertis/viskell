package nl.utwente.viskell.ui.components;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TouchEvent;
import javafx.scene.input.TouchPoint;
import javafx.scene.shape.CubicCurve;
import javafx.scene.transform.Transform;
import nl.utwente.viskell.ui.BlockContainer;
import nl.utwente.viskell.ui.ComponentLoader;
import nl.utwente.viskell.ui.CustomUIPane;

/**
 * A DrawWire represents the UI for a new incomplete connection is the process of being drawn. 
 * It is linked to a single Anchor as starting point, and with a second anchor it produces a new Connection.
 */
public class DrawWire extends CubicCurve implements ChangeListener<Transform>, ComponentLoader {

	/** The 'touch point ID' associated with the mouse. */
	public static final int INPUT_ID_MOUSE = -1;
	
	/** The ID of the touch point that initiated this wire. */
	private final int touchID;
	
    /** The Pane this connection wire is on. */
    private final CustomUIPane pane;

    /** The Anchor this wire has been initiated from */
    private final ConnectionAnchor anchor;
    
    /**
     * @param pane The Pane this wire is on.
     * @param anchor The starting anchor of new wire.
     */
    public DrawWire(CustomUIPane pane, ConnectionAnchor anchor, int touchID) {
        this.setMouseTransparent(true);

        this.touchID = touchID;
        this.pane = pane;
        this.anchor = anchor;
        pane.addWire(this);
        Point2D initPos = pane.sceneToLocal(anchor.localToScene(new Point2D(0, 0)));
        this.setFreePosition(initPos);
        this.invalidateAnchorPosition();
        anchor.localToSceneTransformProperty().addListener(x -> this.invalidateAnchorPosition());
    }

    public static DrawWire initiate(CustomUIPane pane, ConnectionAnchor anchor, int touchID) {
        if (anchor instanceof InputAnchor && ((InputAnchor)anchor).hasConnection()) {
            // make room for a new connection by removing existing one
            Connection conn = ((InputAnchor)anchor).getConnection().get();
            conn.remove();
            // keep the other end of old connection to initiate the new one
            return new DrawWire(pane, conn.getStartAnchor(), touchID);
        } else {
            return new DrawWire(pane, anchor, touchID);
        }

    }

	protected void handleMouseDrag(MouseEvent event) {
		Point2D localPos = pane.sceneToLocal(event.getSceneX(), event.getSceneY());
		this.setFreePosition(localPos);
		event.consume();
	}
	
	protected void handleTouchMove(TouchEvent event) {
		TouchPoint tp = event.getTouchPoint();
		if (tp.getId() == this.touchID) {
			Point2D localPos = pane.sceneToLocal(tp.getSceneX(), tp.getSceneY());
			this.setFreePosition(localPos);
			event.consume();
		}
	}

	protected void handleMouseRelease(MouseEvent event) {
        this.handleReleaseOn(event.getPickResult().getIntersectedNode());
        event.consume();
	}

	protected void handleTouchRelease(TouchEvent event) {
		TouchPoint tp = event.getTouchPoint();
		if (tp.getId() == this.touchID) {
			this.handleReleaseOn(tp.getPickResult().getIntersectedNode());
			event.consume();
		}
		
	}
	
	private void handleReleaseOn(Node picked) {
        if (picked.getParent() instanceof ConnectionAnchor) {
        	ConnectionAnchor target = (ConnectionAnchor)picked.getParent();
            Connection connection = this.buildConnectionTo(target);
            if (connection != null) {
                connection.getStartAnchor().initiateConnectionChanges();
            }
        }
        // drop the wire, even if connection failed
        this.remove();
	}
	
    /**
     * Constructs a new Connection from this partial wire and another anchor
     * @param target the Anchor to which the other end of this should be connection to.
     * @return the newly build Connection or null if it's not possible
     */
    public Connection buildConnectionTo(ConnectionAnchor target) {
        InputAnchor sink;
        OutputAnchor source;
        if (this.anchor instanceof InputAnchor) {
            if (target instanceof InputAnchor) {
                return null;
            }
            sink = (InputAnchor)this.anchor;
            source = (OutputAnchor)target;
        } else {
            if (target instanceof OutputAnchor) {
                return null;
            }
            sink = (InputAnchor)target;
            source = (OutputAnchor)this.anchor;
            
            if (sink.hasConnection()) {
                sink.removeConnections(); // push out the existing connection
            }
        }

        return new Connection(this.pane, source, sink);
    }

    /** Removes this wire from its pane, and its listener. */
    public final void remove() {
    	this.anchor.wireInProgess = null;
        this.anchor.localToSceneTransformProperty().removeListener(this);
        this.pane.removeWire(this);
    }

    @Override
    public void changed(ObservableValue<? extends Transform> observable, Transform oldValue, Transform newValue) {
        this.invalidateAnchorPosition();
    }

    /** Update the UI position of the anchor. */
    private void invalidateAnchorPosition() {
        Point2D center = (this.anchor instanceof InputAnchor) ? new Point2D(0, -4) : new Point2D(0, 4);
        Point2D point = pane.sceneToLocal(this.anchor.localToScene(center));
        this.setStartX(point.getX());
        this.setStartY(point.getY());
        this.updateBezierControlPoints();
    }
    
    /**
     * Sets the free end coordinates for this wire.
     * @param point coordinates local to this wire's parent.
     */
    public void setFreePosition(Point2D point) {
        this.setEndX(point.getX());
        this.setEndY(point.getY());
        this.invalidateAnchorPosition();
        
        Point2D scenePoint = this.pane.localToScene(point, false);
        BlockContainer anchorContainer = this.anchor.getContainer();
        boolean scopeOK = true;
        
        if (this.anchor instanceof OutputAnchor) {
            scopeOK = anchorContainer.getBoundsInScene().contains(scenePoint);
        } else if (this.anchor instanceof InputAnchor) {
            scopeOK = this.pane.getBlockContainers().
                filter(con -> con.getBoundsInScene().contains(scenePoint)).
                    allMatch(con -> anchorContainer.isContainedWithin(con));
        }
        
        if (scopeOK) {
            this.getStrokeDashArray().clear();
        } else if (this.getStrokeDashArray().isEmpty()) {
            this.getStrokeDashArray().addAll(15.0, 15.0);
        }
        
    }

    /** Updates the Bezier offset (curviness) according to the current start and end positions. */
    private void updateBezierControlPoints() {
        double BEZIER_CONTROL_OFFSET = Connection.BEZIER_CONTROL_OFFSET;
        double distY = Math.abs(this.getEndY() - this.getStartY());
        double yOffset = BEZIER_CONTROL_OFFSET;
        if (distY > BEZIER_CONTROL_OFFSET) {
            yOffset = Math.cbrt(distY / BEZIER_CONTROL_OFFSET) * BEZIER_CONTROL_OFFSET;
        }
        
        this.setControlX1(this.getStartX());
        this.setControlX2(this.getEndX());
        if (this.anchor instanceof OutputAnchor) {
            this.setControlY1(this.getStartY() + yOffset);
        } else {
            this.setControlY1(this.getStartY() - yOffset);
        }
        if (this.getStartY() > this.getEndY()) {
            this.setControlY2(this.getEndY()   + yOffset);
        } else {
            this.setControlY2(this.getEndY()   - yOffset);
        }
    }

}
