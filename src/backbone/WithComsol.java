package backbone;

import java.util.ArrayList;

import comsol.*;
import cell.*;

import random.rand;

public class WithComsol {

	public static void Run() throws Exception{
		// Change default parameters
		/////
//		model.L.y = 4e-7;
//		setting.POVScale = 1;
		/////
		CModel.randomSeed = 1;
		/////
		CModel.sticking = true;
		CModel.filament = false;
		CModel.gravity = false;
		CModel.anchoring = false;
		/////
//		model.Kan *= 100.0;
//		model.Kc *= 100.0;
//		model.Kd *= 100.0;
//		model.Kf *= 100.0;
//		model.Kr *= 100.0;
//		model.Ks *= 100.0;
//		model.Kw *= 100.0;
//		model.rhoX = 1020;
		/////
//		model.Kr *= 0.01;
		CModel.growthTimeStep = 24.0*3600.0;
		
		// Initialise random seed
		rand.Seed(CModel.randomSeed);

		// Create cells
		double[][] colour = new double[][]{
				{0.3,0.3,0.3},
				{0.3,0.3,1.0},
				{0.3,1.0,0.3},
				{0.3,1.0,1.0},
				{1.0,0.3,0.3},
				{1.0,0.3,1.0},
				{1.0,1.0,0.3},
				{1.0,1.0,1.0},
				{0.1,0.1,0.1},
				{0.1,0.1,0.4},
				{0.1,0.4,0.1},
				{0.1,0.4,0.4},
				{0.4,0.1,0.1},
				{0.4,0.1,0.4},
				{0.4,0.4,0.1}};
		if(CModel.growthIter==0 && CModel.movementIter==0) {
			// Create initial cells, not overlapping
			for(int iCell = 0; iCell < CModel.NInitCell; iCell++){
				CCell cell = new CCell(rand.IntChoose(CModel.cellType), 	// 0, 1 or 2 by default (specified number is exclusive)
						(0.2*(rand.Double()+0.4))*CModel.L.x, 		// Anywhere between 0.4*Lx and 0.6*Lx
						(0.2*(rand.Double()+0.4))*CModel.L.y, 		// Anywhere between 0.4*Ly and 0.6*Ly
						(0.2*(rand.Double()+0.4))*CModel.L.z,		// Anywhere between 0.4*Lz and 0.6*Lz
						CModel.filament,								// With filament?
						colour[iCell]);										// And a pointer to the model
				// Set cell boundary concentration to initial value
				cell.q = 0.0;
//				for(int ii=0; ii<(cell.type<2?1:2); ii++) {
//					cell.ballArray[ii].pos.y=cell.ballArray[ii].radius;
//				}
//				cell.Anchor();
			}
			boolean overlap = true;
			int[] NSpring = {0,0,0,0};
			while(overlap) {
				CModel.Movement();
				// We want to save the number of springs formed and broken
				NSpring[0] += Assistant.NAnchorBreak;
				NSpring[1] += Assistant.NAnchorForm;
				NSpring[2] += Assistant.NStickBreak;
				NSpring[3] += Assistant.NStickForm;
				if(CModel.DetectCellCollision_Simple(1.0).isEmpty()) 	overlap = false;
			}
			CModel.Write(CModel.cellArray.size() + " initial non-overlapping cells created","iter");
			CModel.Write((NSpring[1]-NSpring[0]) + " anchor and " + (NSpring[3]-NSpring[2]) + " sticking springs formed", "iter");
		}
		
		boolean overlap = false;
		
		// Start server and connect
		CModel.Write("Starting server and connecting model to localhost:" + Assistant.port, "iter");
//		Server.Stop(false);
		Server.Start(Assistant.port);
		Server.Connect(Assistant.port);
		
		
		while(true) {
			// Reset the random seed
			rand.Seed((CModel.randomSeed+1)*(CModel.growthIter+1)*(CModel.movementIter+1));			// + something because if growthIter == 0, randomSeed doesn't matter.

			// Do COMSOL things
			CModel.Write("Calculating cell steady state concentrations (COMSOL)","iter");
			// Make the model
			Comsol comsol = new Comsol();
			CModel.Write("\tInitialising geometry", "iter");
			comsol.Initialise();
			CModel.Write("\tCreating cells", "iter");
			// Create cells in the COMSOL model
			for(CCell cell : CModel.cellArray) {
				if(cell.type<2) 	comsol.CreateSphere(cell);
				else				comsol.CreateRod(cell);
			}
			comsol.CreateBCBox();					// Create a large box where we set the "bulk" conditions
			comsol.BuildGeometry();
			// Set fluxes
			for(CCell cell : CModel.cellArray) {
				comsol.SetFlux(cell);
			}
			CModel.Write("\tSaving model", "iter");
			comsol.Save();							// Save .mph file
			// Calculate and extract the results
			CModel.Write("\tRunning model", "iter");
			comsol.Run();							// Run model to calculate concentrations
			CModel.Write("\tCalculating cell surface concentrations", "iter");
			for(CCell cell : CModel.cellArray) {
				cell.q = comsol.GetParameter(cell, "q" + Integer.toString(cell.type));
			}
			// Clean up after ourselves 
			CModel.Write("\tCleaning model from server", "iter");
			comsol.RemoveModel();

			// Grow cells
			if(!overlap) {
				CModel.Write("Growing cells", "iter");
				int newCell = CModel.GrowthFlux();
				
				// Advance growth
				CModel.growthIter++;
				CModel.growthTime += CModel.growthTimeStep;

				CModel.Write(newCell + " new cells grown, total " + CModel.cellArray.size() + " cells","iter");

				CModel.Write("Resetting springs","iter");
				for(CRodSpring rod : CModel.rodSpringArray) {
					rod.ResetRestLength();
				}
				for(CFilSpring fil : CModel.filSpringArray) 	{
//					fil.ResetSmall();
					fil.ResetBig();
				}
			}

			// Movement
			CModel.Write("Starting movement calculations","iter");
			int nstp = CModel.Movement();
			CModel.movementIter++;
			CModel.movementTime += CModel.movementTimeStep;
			CModel.Write("Movement finished in " + nstp + " solver steps","iter");
			CModel.Write("Anchor springs broken/formed: " + Assistant.NAnchorBreak + "/" + Assistant.NAnchorForm + ", net " + (Assistant.NAnchorForm-Assistant.NAnchorBreak) + ", total " + CModel.anchorSpringArray.size(), "iter");
			CModel.Write("Stick springs broken/formed: " + Assistant.NStickBreak + "/" + Assistant.NStickForm + ", net " + (Assistant.NStickForm-Assistant.NStickBreak) + ", total " + CModel.stickSpringArray.size(), "iter");
			ArrayList<CCell> overlapCellArray = CModel.DetectCellCollision_Simple(1.0);
			if(!overlapCellArray.isEmpty()) {
				CModel.Write(overlapCellArray.size() + " overlapping cells detected, growth delayed","warning");
				String cellNumber = "" + overlapCellArray.get(0).Index();
				for(int ii=1; ii<overlapCellArray.size(); ii++) 	cellNumber += " & " + overlapCellArray.get(ii).Index();
				CModel.Write("Cell numbers " + cellNumber,"iter");
				overlap = true;
			} else {
				overlap = false;
			}

			// Plot
			if(Assistant.plot) {
				CModel.Write("Writing and rendering POV files","iter");
				CModel.POV_Write(Assistant.plotIntermediate);
				CModel.POV_Plot(Assistant.plotIntermediate); 
			}
			
			// And finally: save stuff
			CModel.Write("Saving model as .mat file", "iter");
			CModel.Save();
		}
	}
}