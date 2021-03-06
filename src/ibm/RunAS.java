package ibm;

import java.util.ArrayList;

import random.rand;
import ser2mat.ser2mat;

public class RunAS extends Run { 		// simulation == 2
	
	public RunAS(Model model) {
		this.model = model;
	}
	
	public void Initialise() {
		model.Write("Loading parameters for AS","");
		// Load default parameters
		int filF = 0;
		int flocF = 1;
		int shapeFlocF = model.shapeX[flocF] = 0;
		model.L = new Vector3d(30e-6, 30e-6, 30e-6);
		model.Linit = new Vector3d(7e-6, 7e-6, 7e-6);
		model.MWX[filF] = model.MWX[flocF] = 24.6e-3;									// [kg mol-1]
		model.rhoX[filF] = model.rhoX[flocF] = 1010; 									// [kg m-3]
		model.radiusCellMax[filF] = 0.5e-6;												// [m] (Lau 1984)
		if(shapeFlocF==0) 						model.radiusCellMax[flocF] = 0.52e-6; 	// Same volume as below
		else 									model.radiusCellMax[flocF] = 0.35e-6;	// [m] (Lau 1984) 		
		model.lengthCellMax[filF] = 4e-6;												// [m] (Lau 1984), compensated for model length = actual length - 2*r
		if(shapeFlocF==1 || shapeFlocF==2)		model.lengthCellMax[flocF] = 1.1e-6;	// [m] (Lau 1984), compensated
		model.muAvgSimple[filF] = 0.271;												// [h-1] muMax = 6.5 day-1 = 0.271 h-1, S. natans, (Lau 1984). Monod coefficient *should* be low (not in Lau) so justified high growth versus species 5. 
		model.muAvgSimple[flocF] = 0.383;												// [h-1] muMax = 9.2 day-1 = 0.383 h-1, "floc former" (Lau 1984). Monod coefficient *should* be high (not in Lau)
		model.muStDev[filF]  = 0.2*model.muAvgSimple[filF];								// Defined as one fifth
		model.muStDev[flocF] = 0.2*model.muAvgSimple[flocF];
		model.NCellInit = 18;
//		model.NCellInit = 18*2; 			// For multiple flocs 
		model.growthTimeStep = 300.0;
		model.attachCellType = 5;
		model.attachNotTo = new int[]{};
		model.filament = true;
		model.filType[filF] = true;			// Only filament former forms filaments
		model.sticking = true;
		model.Ks[filF][flocF] = model.Ks[flocF][filF] = model.Ks[filF][filF] = model.Ks[flocF][flocF] = 1e-11;
		model.stickType[filF][flocF] = model.stickType[flocF][filF] = model.stickType[filF][filF] = model.stickType[flocF][flocF] = true;	// Anything sticks
		model.anchoring = false;
		model.normalForce = false;
		model.electrostatic = false;
	}
	
	public void Start() {
		model.UpdateDependentParameters();		// Update model parameters
		int filF = model.filF;
		int flocF = model.flocF;
		// Initialise model if we are starting a new simulation
		if(model.growthIter == 0 && model.relaxationIter == 0) {
			// Set initial cell parameters based on model
			rand.Seed(model.randomSeed);
			int[] typeInit = new int[model.NCellInit];
			double[] nInit = new double[model.NCellInit];
			double[] radiusModifier = new double[model.NCellInit];
			Vector3d[] directionInit = new Vector3d[model.NCellInit];
			Vector3d[] position0Init = new Vector3d[model.NCellInit];
			Vector3d[] position1Init = new Vector3d[model.NCellInit];
			
			// Create parameters for new cells
			for(int ii=0; ii<model.NCellInit; ii++){
				if(model.nCellMax[flocF]>model.nCellMax[filF]) {
					final int div = (int) (model.nCellMax[flocF] / model.nCellMax[filF]) + 1;	// e.g. 5 is 3x heavier --> div is 1/4, so there will be 3x more 4 cells than 5
					typeInit[ii] = ii%div==0 ? flocF : filF;
				} else {
					final int div = (int) (model.nCellMax[filF] / model.nCellMax[flocF]) + 1;
					typeInit[ii] = ii%div==0 ? filF : flocF;
				}
			}
			for(int ii=0; ii<model.NCellInit; ii++) {
				nInit[ii] = 0.5*model.nCellMax[typeInit[ii]] * (1.0 + rand.Double());
				radiusModifier[ii] = 0.0; 
				final double restLength =  RodSpring.RestLength(Ball.Radius(nInit[ii], typeInit[ii], model), nInit[ii], typeInit[ii], model);
				directionInit[ii] = new Vector3d((rand.Double()-0.5), 							(rand.Double()-0.5), 							(rand.Double()-0.5)).normalise();
				position0Init[ii] = new Vector3d((rand.Double()-0.5)*model.Linit.x, 	(rand.Double()-0.5)*model.Linit.y, 	(rand.Double()-0.5)*model.Linit.z);
//				if(ii>model.NCellInit/2) {
//					position0Init[ii] = position0Init[ii].plus(new Vector3d(50e-6, 0e-6, 0e-6)); 	// Displace half the cells by a number of microns in X direction (two initial flocs)
//				}
				position1Init[ii] = position0Init[ii].plus(directionInit[ii].times(restLength));
			}
			
			// Create initial cells
			for(int iCell = 0; iCell < model.NCellInit; iCell++){
				boolean filament = model.filament && model.filType[typeInit[iCell]];
				@SuppressWarnings("unused")
				Cell cell = new Cell(typeInit[iCell], 				// Type of biomass
						nInit[iCell],
						radiusModifier[iCell],
						position0Init[iCell],
						position1Init[iCell],
						filament,										// With capability to form filaments?
						model);
			}
			model.Write(model.cellArray.size() + " initial cells created","iter");
	
			// Save and convert the file
			model.Save();
			ser2mat.Convert(model);	
		}

		// Start growth/relaxation loop
		while(model.growthIter<model.growthIterMax) {
			// Reset the random seed
			rand.Seed((model.randomSeed+1)*(model.growthIter+1)*(model.relaxationIter+1));	// + something because if growthIter == 0, randomSeed doesn't matter.

			// Grow cells
			model.Write("Growing cells", "iter");
			model.GrowthSimple();
			// Mark mother cell for division if ready
			ArrayList<Cell> dividingCellArray = new ArrayList<Cell>(0);
			for(Cell mother : model.cellArray) {
				if(mother.GetAmount() > model.nCellMax[mother.type])
					dividingCellArray.add(mother);
			}
			// Divide marked cells
			int NFil = 0; int NBranch = 0;													// Keep track of how many filament springs and how many new branches we make
			for(Cell mother : dividingCellArray) {
				Cell daughter = model.DivideCell(mother);
				int shapeMother = model.shapeX[mother.type];
				if(mother.filament) {
					if(shapeMother==0) {
						if(model.filSphereStraightFil)
							model.TransferFilament(mother, daughter);
						model.CreateFilament(mother, daughter);
						NFil += 1;
					} else if(shapeMother==1 || shapeMother==2) {
						Cell neighbourDaughter = mother.GetNeighbour();
						if(mother.filSpringArray.size()>2 && rand.Double() < model.filRodBranchFrequency && neighbourDaughter != null) {
							model.CreateFilament(daughter, mother, neighbourDaughter);		// 3 arguments --> branched, 2 springs daughter to mother and 2 daughter to neighbour 
							NFil += 4; NBranch++;
						} else {															// If we insert the cell in the straight filament
							model.TransferFilament(mother, daughter);		 
							model.CreateFilament(mother, daughter);							// 2 arguments --> unbranched, 2 springs daughter to mother
							NFil += 2;
						}
					} else
						throw new IndexOutOfBoundsException("Unknown mother cell type: " + mother.type);
				}
			}
			// Advance growth
			model.growthIter++;
			model.growthTime += model.growthTimeStep;
			if(dividingCellArray.size()>0) {
				model.Write(dividingCellArray.size() + " cells divided, total " + model.cellArray.size() + " cells","iter");
				model.Write(NFil + " filament springs formed, " + NBranch + " new branches", "iter");
			}
			// Reset springs where needed
			model.Write("Resetting springs","iter");
			for(Spring rod : model.rodSpringArray) 	rod.ResetRestLength();
			for(Spring fil : model.filSpringArray) 	fil.ResetRestLength();
			// Attach new cells
			if(model.attachmentRate > 0) {
				final double NNew = model.attachmentRate*(model.growthTimeStep/3600.0);
				model.attachCounter += NNew;
				model.Write("Attaching " + (int)model.attachCounter + " new cells", "iter");
				model.Attachment( (int) model.attachCounter );
				model.attachCounter -= (int) model.attachCounter;	// Subtract how many cells we've added this turn
			}
				
			// Relaxation
			int relaxationNIter = (int) (model.relaxationTimeStep/model.relaxationTimeStepdt);
			model.Write("Starting relaxation calculations","iter"); 
			int NAnchorBreak = 0, NAnchorForm = 0, NStickBreak = 0, NStickForm = 0, NFilBreak = 0;
			for(int ir=0; ir<relaxationNIter; ir++) {
				int[] relaxationOut = model.Relaxation();
				int nstp 	=  relaxationOut[0]; 
				NAnchorBreak+= relaxationOut[1];
				NAnchorForm	+= relaxationOut[2];
				NStickBreak += relaxationOut[3];
				NStickForm 	+= relaxationOut[4];
				NFilBreak 	+= relaxationOut[5];
				model.relaxationIter++;
				model.relaxationTime += model.relaxationTimeStepdt;
				model.Write("    Relaxation finished in " + nstp + " solver steps","iter");
				// And finally: save stuff
				model.Save();
				ser2mat.Convert(model);
			}
			model.Write("Anchor springs broken/formed: " + NAnchorBreak + "/" + NAnchorForm + ", net " + (NAnchorForm-NAnchorBreak) + ", total " + model.anchorSpringArray.size(), "iter");
			model.Write("Filament springs broken: "      + NFilBreak          														+ ", total " + model.filSpringArray.size(), "iter");
			model.Write("Stick springs broken/formed: "  + NStickBreak  + "/" + NStickForm  + ", net " + (NStickForm-NStickBreak) 	+ ", total " + model.stickSpringArray.size(), "iter");
		}
	}
}
