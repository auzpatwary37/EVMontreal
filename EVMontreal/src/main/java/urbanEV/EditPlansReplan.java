package urbanEV;

import java.util.List;

import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.router.StageActivityTypeIdentifier;

public class EditPlansReplan {
	public static Activity findRealActAfter(Plan plan, int index) {
		
		List<PlanElement> planElements = plan.getPlanElements() ;
		return (Activity) planElements.get( findIndexOfRealActAfter(plan, index) ) ; 
	}
	
	public static int findIndexOfRealActAfter(Plan plan, int index) {
		
		List<PlanElement> planElements = plan.getPlanElements() ;

		int theIndex = -1 ;
		for ( int ii=planElements.size()-1 ; ii>index; ii-- ) {
			if ( planElements.get(ii) instanceof Activity ) {
				Activity act = (Activity) planElements.get(ii) ;
				if ( !StageActivityTypeIdentifier.isStageActivity( act.getType() ) ) {
					theIndex = ii ;
				}
			}
		}
		return theIndex ;
	}
	
	public static Activity findRealActBefore(Plan plan, int index) {
		
		List<PlanElement> planElements = plan.getPlanElements() ;

		Activity prevAct = null ;
		for ( int ii=0 ; ii<index ; ii++ ) {
			if ( planElements.get(ii) instanceof Activity ) {
				Activity act = (Activity) planElements.get(ii) ;
				if ( !StageActivityTypeIdentifier.isStageActivity( act.getType() ) ) {
					prevAct = act ;
				}
			}
		}
		return prevAct;
	}
}
