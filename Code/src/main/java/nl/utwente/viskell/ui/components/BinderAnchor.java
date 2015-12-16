package nl.utwente.viskell.ui.components;

import java.util.Set;

import nl.utwente.viskell.haskell.expr.Binder;
import nl.utwente.viskell.haskell.expr.LetExpression;
import nl.utwente.viskell.ui.BlockContainer;

/** An internal output anchor for an argument binder. */
public class BinderAnchor extends OutputAnchor {

    private final WrappedContainer container;

    // FIXME BinderAnchor should not have to use the Block parent
    public BinderAnchor(WrappedContainer container, Block parent, Binder binder) {
        super(parent, binder);
        this.container = container;
    }

    @Override
    protected void extendExprGraph(LetExpression exprGraph, BlockContainer container, Set<OutputAnchor> outsideAnchors) {
        return; // the scope of this graph is limited to its parent container
    }
    
    @Override
    public void initiateConnectionChanges() {
        // Starts a new (2 phase) change propagation process from this lambda.
        container.handleConnectionChanges(false);
        container.handleConnectionChanges(true);
    }

    @Override
    public void prepareConnectionChanges() {
        this.container.refreshAnchorTypes();
    }

    @Override
    protected void handleConnectionChanges(boolean finalPhase) {
        this.container.handleConnectionChanges(finalPhase);
    }
}
