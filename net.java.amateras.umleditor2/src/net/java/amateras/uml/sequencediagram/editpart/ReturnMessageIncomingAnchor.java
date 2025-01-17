/**
 * 
 */
package net.java.amateras.uml.sequencediagram.editpart;

import net.java.amateras.uml.model.AbstractUMLEntityModel;
import net.java.amateras.uml.sequencediagram.model.ActivationModel;

import org.eclipse.draw2d.AbstractConnectionAnchor;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.geometry.Point;

/**
 * @author Takahiro Shida.
 *
 */
public class ReturnMessageIncomingAnchor extends AbstractConnectionAnchor {

	private AbstractUMLEntityModel relation;
	
	
	public ReturnMessageIncomingAnchor(IFigure owner, AbstractUMLEntityModel relation) {
		super(owner);
		this.relation = relation;
	}
	
	/* (뷄 Javadoc)
	 * @see org.eclipse.draw2d.ConnectionAnchor#getLocation(org.eclipse.draw2d.geometry.Point)
	 */
	public Point getLocation(Point reference) {
		Point point = getOwner().getBounds().getCopy().getTopLeft();
		if (relation.getConstraint().x > point.x) {
			point.translate(ActivationModel.DEFAULT_WIDTH,0);
		}
		point.y = relation.getConstraint().y + relation.getConstraint().height;
		InteractionEditPart.getLayer().translateToAbsolute(point);
		return point;
	}

}
