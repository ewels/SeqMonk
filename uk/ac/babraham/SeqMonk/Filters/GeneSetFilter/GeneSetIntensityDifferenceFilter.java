/**
 * Copyright Copyright 2010-15 Simon Andrews
 *
 *    This file is part of SeqMonk.
 *
 *    SeqMonk is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    SeqMonk is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with SeqMonk; if not, write to the Free Software
 *    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package uk.ac.babraham.SeqMonk.Filters.GeneSetFilter;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Hashtable;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.apache.commons.math3.stat.regression.SimpleRegression;


import uk.ac.babraham.SeqMonk.SeqMonkApplication;
import uk.ac.babraham.SeqMonk.SeqMonkException;
import uk.ac.babraham.SeqMonk.Analysis.Statistics.BenjHochFDR;
import uk.ac.babraham.SeqMonk.Analysis.Statistics.MappedGeneSetTTestValue;
import uk.ac.babraham.SeqMonk.Analysis.Statistics.SimpleStats;
import uk.ac.babraham.SeqMonk.Analysis.Statistics.TTest;
import uk.ac.babraham.SeqMonk.DataTypes.DataCollection;
import uk.ac.babraham.SeqMonk.DataTypes.DataStore;
import uk.ac.babraham.SeqMonk.DataTypes.Probes.Probe;
import uk.ac.babraham.SeqMonk.DataTypes.Probes.ProbeList;
import uk.ac.babraham.SeqMonk.DataTypes.Probes.ProbeSet;
import uk.ac.babraham.SeqMonk.Dialogs.ProbeListSelectorDialog;
import uk.ac.babraham.SeqMonk.Dialogs.Renderers.TypeColourRenderer;
import uk.ac.babraham.SeqMonk.Filters.ProbeFilter;
import uk.ac.babraham.SeqMonk.GeneSets.GeneSetCollection;
import uk.ac.babraham.SeqMonk.GeneSets.GeneSetCollectionParser;
import uk.ac.babraham.SeqMonk.GeneSets.MappedGeneSet;
import uk.ac.babraham.SeqMonk.Preferences.SeqMonkPreferences;
import uk.ac.babraham.SeqMonk.Utilities.NumberKeyListener;

/**
 * Filters probes based on the the probability of their difference being
 * part of the local noise level for their average intensity.
 * 
 * interface for p value, benhoch correction
 * 
 * TODO: sort out no of matched probes when existing probe lists are used - when they don't have a quantitated value, the code is a bit messy - kick out unquantitated probes.
 * 
 * TODO: enable mixed directional 
 * 
 * TODO: annotate this code properly ....
 * 
 * 
 */
public class GeneSetIntensityDifferenceFilter extends ProbeFilter implements WindowListener{

	/** The following are all options that can be changed in the options panel */
	/* The p value (or q value) threshold that we use to filter the results */
	private Double pValueLimit = 0.05;
	
	/* The default minimum absolute z-score that we use to filter the results */
	private Double zScoreThreshold = 0.2;
	
	/* The mean values for each distribution slice */
	protected float [][] customRegressionValues = null;
	
	/* This holds the indices to get the deduplicated probe from the original list of probes */
	private Integer[] deduplicatedIndices;
	
	/* calculate mean values or not */
	protected boolean calculateCustomRegression = false;
	
	/* whether to apply a multiple testing correction */
	private boolean applyMultipleTestingCorrection = true;
	
	/* calculate linear regression line */
	protected boolean calculateLinearRegression = false;

	/* number of (deduplicated) probes to use in each distribution */
	private int probesPerSet;
	
	/* minimum number of genes in geneset for it to be imported */
	private int minGenesInSet = 5;
	
	/* maximum number of genes in geneset for it to be imported */
	private int maxGenesInSet = 500;

	/* whether a geneSetFile has been selected - not sure how thoroughly the validity gets checked....*/ 
//	private boolean validGeneSetFile = false;
	
	/* the filepath */
	private static String validGeneSetFilepath;
	
	/* the probelists selected to be used as the gene sets*/ 
	private ProbeList [] selectedProbeLists = null;

	/* The z-scores that we calculate */ 
	protected Hashtable<Probe, Double>probeZScoreLookupTable;
	
	/* The probes after they've been filtered for NAs */
	protected Probe [] probes;
	
	/* The regression object */
	protected SimpleRegression simpleRegression = null;
	
	/**************/
	
	// We only want to compare 2 datastores but I've kept these as arrays so they're compatible with other Seqmonk methods - am sure it could be neatened up though
	private DataStore [] fromStores = new DataStore[0];
	private DataStore [] toStores = new DataStore[0];
	
	/* the first datastore to compare */
	protected DataStore fromStore;
	
	/* the second datastore */ 
	protected DataStore toStore;
	
	/* the options panel */
	private DifferencesOptionsPanel optionsPanel;
	
	/* the data collection */
	private DataCollection dataCollection;
	
	private GeneSetDisplay geneSetDisplay;
	
	
	/**
	 * Instantiates a new differences filter with default options.
	 * 
	 * @param collection The dataCollection to filter
	 * @throws SeqMonkException if the collection isn't quantitated
	 */
	public GeneSetIntensityDifferenceFilter (DataCollection collection) throws SeqMonkException {
		super(collection);
		this.dataCollection = collection; // is this right?
		
		// Put out a warning if we see that we're not using all possible probes
		// for the test.
		if (!(startingList instanceof ProbeSet)) {
			JOptionPane.showMessageDialog(SeqMonkApplication.getInstance(), "<html>This test requires a representative set of all probes to be valid.<br>Be careful running it on a biased subset of probes</html>", "filtered list used", JOptionPane.WARNING_MESSAGE);
		}
		
		// We need to work out how many probes are going to be put into
		// each sub-distribution we calculate.  The rule is going to be that
		// we use 1% of the total, or 100 probes whichever is the higher 
		
		Probe [] probes = startingList.getAllProbes();

		probesPerSet = probes.length/100;
		if (probesPerSet < 100) probesPerSet = 100;
		if (probesPerSet > probes.length) probesPerSet = probes.length;		
		// Now that we've got the deduplicated analysis, we might need to reduce the number of probesPerSet but this will have to be done later when they've been calculated.		

		
		
		optionsPanel = new DifferencesOptionsPanel();
		
	}

	/* (non-Javadoc)
	 * @see uk.ac.babraham.SeqMonk.Filters.ProbeFilter#generateProbeList()
	 */
	protected void generateProbeList() {
		

		try {
			
			/* get selected probelists  */
		/*	if(optionsPanel.probeListRadioButton.isSelected()){
				ProbeList [] probeLists = ProbeListSelectorDialog.selectProbeLists();
				if (probeLists == null || probeLists.length == 0){
					
					JOptionPane.showMessageDialog(SeqMonkApplication.getInstance(), "No probelists were selected.",  "No probe lists selected", JOptionPane.INFORMATION_MESSAGE);
					progressCancelled();
					return;
				}			
				selectedProbeLists = probeLists;				
			}
			
			else if(optionsPanel.geneSetsFileRadioButton.isSelected()){
				do stuff
				
			}
			*/
			
			applyMultipleTestingCorrection = optionsPanel.multipleTestingBox.isSelected();
			//calculateLinearRegression = optionsPanel.calculateLinearRegressionBox.isSelected();
			calculateCustomRegression = optionsPanel.calculateCustomRegressionBox.isSelected();			 
			
			if(calculateCustomRegression == false){
				customRegressionValues = null;
			}
			
			if(calculateLinearRegression){
				simpleRegression = new SimpleRegression();
			}
			
			Probe [] allProbes = startingList.getAllProbes();
			
			// We're not allowing multiple comparisons - this is a bit of a messy workaround so it's compatible with other methods.
			fromStore = fromStores[0];
			toStore = toStores[0];	
			
			ArrayList<Probe> probeArrayList = new ArrayList<Probe>();
			
			int NaNcount = 0;
			// remove the invalid probes - the ones without a value
			for (int i=0;i<allProbes.length;i++) {
				if((Float.isNaN(fromStore.getValueForProbe(allProbes[i]))) || (Float.isNaN(toStore.getValueForProbe(allProbes[i])))) {
					NaNcount++;
				}
				else{
					probeArrayList.add(allProbes[i]);
				}
			}
			
			System.err.println("Found " + NaNcount + " probes that were invalid.");
			
			//Probe[] 
			probes = probeArrayList.toArray(new Probe[0]);
			
			if (calculateCustomRegression == true){
				customRegressionValues = new float [2][probes.length];
			}			

			// We'll pull the number of probes to sample from the preferences if they've changed it
			Integer updatedProbesPerSet = optionsPanel.probesPerSet();
			if (updatedProbesPerSet != null) probesPerSet = updatedProbesPerSet;
	
			// we want a set of z-scores using the local distribution.			
			probeZScoreLookupTable = new Hashtable<Probe, Double>();
								
			// Put something in the progress whilst we're ordering the probe values to make the comparison.
			progressUpdated("Generating background model",0,1);	
			
			Comparator<Integer> comp = new AverageIntensityComparator(fromStore, toStore, probes);			
			
			// We need to generate a set of probe indices that can be ordered by their average intensity			
			Integer [] indices = new Integer[probes.length];

			for (int i=0;i<probes.length;i++) {
				
				indices[i] = i;
				
				/* add the data to the linear regression object */ 
				if(calculateLinearRegression){
					simpleRegression.addData((double)fromStore.getValueForProbe(probes[i]), (double)toStore.getValueForProbe(probes[i]));
				}	
			}
			
			if(calculateLinearRegression){
			
				System.out.println("intercept = " + simpleRegression.getIntercept() + ", slope = " + simpleRegression.getSlope());
			}
			
			Arrays.sort(indices,comp);
			
			/* This holds the indices to get the deduplicated probe from the original list of probes */
			deduplicatedIndices = new Integer[probes.length];
			
			/* The number of probes with different values */
			int dedupProbeCounter = 0;
			
			// the probes deduplicated by value
			ArrayList<Probe> deduplicatedProbes = new ArrayList<Probe>();
			
			// populate the first one so that we have something to compare to in the loop			
			deduplicatedIndices[0] = 0;
			deduplicatedProbes.add(probes[indices[0]]);
			
			progressUpdated("Made 0 of 1 comparisons", 0, 1);
			
			for (int i=1;i<indices.length;i++) {
				
				/* indices have been sorted, now we need to check whether adjacent pair values are identical */
				if((fromStore.getValueForProbe(probes[indices[i]]) == fromStore.getValueForProbe(probes[indices[i-1]])) && 
						(toStore.getValueForProbe(probes[indices[i]]) == toStore.getValueForProbe(probes[indices[i-1]]))){

					/* If they are identical, do not add the probe to the deduplicatedProbes object, but have a reference for it so we can look up which deduplicated probe and 
					 * therefore which distribution slice to use for the duplicated probe. */
					deduplicatedIndices[i] = dedupProbeCounter;
					
				}
				else{

					deduplicatedProbes.add(probes[indices[i]]);
					dedupProbeCounter++;
					deduplicatedIndices[i] = dedupProbeCounter;
				}
			}	
			
			Probe [] dedupProbes = deduplicatedProbes.toArray(new Probe[0]);
						
			
			// make sure we're not trying to use more probes than we've got in the analysis
			if(probesPerSet > dedupProbes.length){
				probesPerSet = dedupProbes.length;			
			}

			System.out.println("total number of probe values = " + probes.length);	
			System.out.println("number of deduplicated probe values = " + dedupProbes.length);	
			System.out.println("probesPerSet = " + probesPerSet);			
			
			// I want this to contain all the differences, then from that I'm going to get the z-scores.
			double [] currentDiffSet = new double[probesPerSet];
			
			for (int i=0;i<indices.length;i++) {	
				if (cancel) {
					cancel = false;
					progressCancelled();
					return;
				}
				
				if (i % 1000 == 0) {
					
					int progress = (i*100)/indices.length;
					
					//progress += 100*comparisonIndex;					
					progressUpdated("Made 0 out of 1 comparisons",progress,100);
				}				
				
//				boolean print;
//				if(probes[indices[i]].name().startsWith("Apoa4")){
//					print = true;
//				}
//				else{
//					print = false;
//				}
				
				/**
				 * There are +1s in here because we skip over j when j == startingIndex.
				 * 
				 */
				// We need to make up the set of differences to represent this probe
				int startingIndex = deduplicatedIndices[i] - (probesPerSet/2);
				if (startingIndex < 0) startingIndex = 0;
				if (startingIndex+(probesPerSet+1) >= dedupProbes.length) startingIndex = dedupProbes.length-(probesPerSet+1);

				/*System.err.println("i = " + i);
				System.err.println("deduplicatedIndices[i] = " + deduplicatedIndices[i]);
				System.err.println("starting index = " + startingIndex);
				System.err.println("starting index + probesPerSet = " + (startingIndex+probesPerSet));
				//System.out.println("currentDiffsetLength = " + currentDiffSet.length);
				*/
				try {
					for (int j=startingIndex;j<startingIndex+(probesPerSet+1);j++) {
					//for (int j=startingIndex;j<startingIndex+(probesPerSet);j++) {	

						if (j == startingIndex) {

							continue; // Don't include the point being tested in the background model
						}
						
						double diff;
						
						if(calculateLinearRegression == true){
							
							if(j >  dedupProbes.length){
								
								System.err.println(" j is too big, it's " + j + " and dedupProbes.length = " + dedupProbes.length + ", starting index = " + startingIndex);
							}
							double x = fromStore.getValueForProbe(dedupProbes[j]);
							double expectedY =  (simpleRegression.getSlope() * x) + simpleRegression.getIntercept();
							diff =  toStore.getValueForProbe(dedupProbes[j]) - expectedY;
							
						}
						
						else {

							diff =  toStore.getValueForProbe(dedupProbes[j]) - fromStore.getValueForProbe(dedupProbes[j]);
						}
						
						if (j < startingIndex) {
													
							currentDiffSet[j-startingIndex] = diff;
							
						}
						else {

							currentDiffSet[(j-startingIndex)-1] = diff;
							
						}
					}

	
					if (calculateCustomRegression == true){
						// the average/ kind of centre line
						float z = ((fromStore.getValueForProbe(probes[indices[i]]) + toStore.getValueForProbe(probes[indices[i]]))/2);
						customRegressionValues[0][indices[i]] = z - ((float)SimpleStats.mean(currentDiffSet)/2);
						customRegressionValues[1][indices[i]] = z + ((float)SimpleStats.mean(currentDiffSet)/2);
						
						//if((i < 10) || (i % 1000 == 0)){
						/*if((probes[indices[i]].name().startsWith("Nr2f6")) || probes[indices[i]].name().startsWith("Per1")){
							System.err.println("");
							System.err.println("For " + probes[indices[i]].name() + ", toValue = " + toStore.getValueForProbe(probes[indices[i]]) 
									+ ", from value = " + fromStore.getValueForProbe(probes[indices[i]])  + ", z = " + z + ", mean " + SimpleStats.mean(currentDiffSet) + ", new x = " + customRegressionValues[0][indices[i]] +
							", new y = " + customRegressionValues[1][indices[i]]  + ", i = " + i);
						}
						*///customRegressionValues[indices[i]] = (float)SimpleStats.median(currentSetX);
					}					
					
					double mean = 0;										
					
					// Get the difference for this point
					double diff;
					
					if(calculateLinearRegression == true){
						
						double x = fromStore.getValueForProbe(probes[indices[i]]);
						double expectedY =  (simpleRegression.getSlope() * x) + simpleRegression.getIntercept();
						diff =  toStore.getValueForProbe(probes[indices[i]]) - expectedY;
						
					}
					
					else if(calculateCustomRegression == true){
						
						// y-x
						diff = toStore.getValueForProbe(probes[indices[i]]) - fromStore.getValueForProbe(probes[indices[i]]);			
								
						mean = SimpleStats.mean(currentDiffSet);
						
					/*	if((probes[indices[i]].name().startsWith("Nr2f6")) || probes[indices[i]].name().startsWith("Per1")){
							print = true;
							System.err.println();
							System.err.println("toStore = " + toStore.name());
							System.err.println(probes[indices[i]].name()  + ", toStore = " + toStore.getValueForProbe(probes[indices[i]]) + ", fromStore = " + fromStore.getValueForProbe(probes[indices[i]]) 
							+ ", customRegressionValues[0] = " + customRegressionValues[0][indices[i]] + ", customRegressionValues[1] = " + customRegressionValues[1][indices[i]] + ", diff = " + diff + ", mean = " + mean);
							System.err.println();
						}
						else{
							print = false;
						}						
					*/			
					}
					
					else {
						diff =  toStore.getValueForProbe(probes[indices[i]]) - fromStore.getValueForProbe(probes[indices[i]]);
					}
					
					double stdev = SimpleStats.stdev(currentDiffSet,mean);
					// if there are no reads in the probe for either of the datasets, should we set the zscore to 0?? 						
					double zScore = (diff - mean) / stdev;
	
					
						// modified z score
						// median absolute deviation
				/*		double[] madArray = new double[currentDiffSet.length];
						double median = SimpleStats.median(currentDiffSet);					
						
						for(int d=0; d<currentDiffSet.length; d++){
							
							madArray[d] = Math.abs(currentDiffSet[d] - median);
							
						}
						
						double mad = SimpleStats.median(madArray);
							
						zScore = (0.6745 * (diff - median))/mad;
					}
				*/	probeZScoreLookupTable.put(probes[indices[i]], zScore);										
				}
				catch (SeqMonkException sme) {
					progressExceptionReceived(sme);
					return;
				}

			}

			// make this an array list as we're kicking out the mapped gene sets that have zscores with variance of 0.
			ArrayList <MappedGeneSetTTestValue> pValueArrayList = new ArrayList<MappedGeneSetTTestValue>();
			
			MappedGeneSet [] mappedGeneSets = null;
			
			/* if we're using the gene set from a file, map the gene sets to the probes */
			if(optionsPanel.geneSetsFileRadioButton.isSelected()){
				
				GeneSetCollectionParser geneSetCollectionParser = new GeneSetCollectionParser(minGenesInSet, maxGenesInSet);
				GeneSetCollection geneSetCollection = geneSetCollectionParser.parseGeneSetInformation(validGeneSetFilepath);				
				MappedGeneSet [] allMappedGeneSets = geneSetCollection.getMappedGeneSets(probes);
				
				if(allMappedGeneSets == null){
					throw new SeqMonkException("No sets of genes could be matched to probes.\nTo use gene sets from a file, probe names must contain the gene name.\nTry defining new probes over genes or use existing probes lists instead of a gene set file.");	
					//JOptionPane.showMessageDialog(SeqMonkApplication.getInstance(), "No sets of genes could be matched to probes.\nTo use gene sets from a file, probe names must contain the gene name.\nTry defining new probes over genes or use existing probes lists instead of a gene set file.", "No gene sets matched", JOptionPane.ERROR_MESSAGE);
				}
				
				else{
				
					ArrayList <MappedGeneSet> mgsArrayList = new ArrayList<MappedGeneSet>();
					
					/* get rid of those that have fewer probes in the set than minGenesInSet. We shouldn't exceed maxGenesInSet unless probes have been made over something other than genes */ 
					for (int i=0; i<allMappedGeneSets.length; i++){
						if(allMappedGeneSets[i].getProbes().length >= minGenesInSet){
							mgsArrayList.add(allMappedGeneSets[i]);
						}
					}
					mappedGeneSets = mgsArrayList.toArray(new MappedGeneSet[0]);
				}	
			}
			/* or if we're using existing probelists, create mappedGeneSets from them */
			else if(optionsPanel.probeListRadioButton.isSelected() && selectedProbeLists != null){
				mappedGeneSets = new MappedGeneSet[selectedProbeLists.length];						
				
				for (int i=0; i<selectedProbeLists.length; i++){
					mappedGeneSets[i] = new MappedGeneSet(selectedProbeLists[i]);
				}
			}
			else{					
				throw new SeqMonkException("Haven't got any genesets to use, shouldn't have got here without having any selected.");		
			}
			
			if(mappedGeneSets == null || mappedGeneSets.length == 0){
				throw new SeqMonkException("Couldn't map gene sets to the probes, try again with a different probe set");				
			}		
			
			else{
				System.err.println("there are " + mappedGeneSets.length + " mappedGeneSets");
				System.err.println("size of zScore lookup table = "+ probeZScoreLookupTable.size());
			}
			
			/* we need to go through the mapped gene set and get all the values for the matched probes */
			for(int i=0; i<mappedGeneSets.length; i++){
				
				Probe [] geneSetProbes = mappedGeneSets[i].getProbes();

				// to contain all the z-scores for the gene set
				double [] geneSetZscores = new double [geneSetProbes.length];								
									
				// Find the z-scores for each of the probes in the mappedGeneSet				
				for (int gsp = 0; gsp < geneSetProbes.length; gsp++){
				
					if (probeZScoreLookupTable.containsKey(geneSetProbes[gsp])) {
													
						geneSetZscores[gsp] = probeZScoreLookupTable.get(geneSetProbes[gsp]); 
					}				
				}	
				
				// this is just temporary to check the stats - there's some duplication with this.
				mappedGeneSets[i].zScores = geneSetZscores;
								
				if(geneSetZscores.length > 1){ // but the number of probes in the mappedGeneSet should always be > 1 anyway as the mappedGeneSet shouldn't be created if there are < 2 matched probes.					
					
					double pVal = TTest.calculatePValue(geneSetZscores, 0);					
						
					mappedGeneSets[i].meanZScore = SimpleStats.mean(geneSetZscores); 
					//mappedGeneSets[i].meanZScore = SimpleStats.median(geneSetZscores); 
					
					// check the variance - we don't want variances of 0.
					double stdev = SimpleStats.stdev(geneSetZscores);
					if((stdev * stdev) < 0.00000000000001){
						continue;
					}
					// if all the differences between the datasets are 0 then just get rid of them
					else if(Double.isNaN(pVal)){
						continue;													
					}
					else{
						pValueArrayList.add(new MappedGeneSetTTestValue(mappedGeneSets[i], pVal));							
					}	
				}
				else{
					System.err.println("Fell through the net somewhere, why does the set of zscores contain fewer than 2 values?");
					continue;

				}                 
			}
			
			MappedGeneSetTTestValue [] filterResultpValues = pValueArrayList.toArray(new MappedGeneSetTTestValue[0]);
			
			ArrayList <MappedGeneSetTTestValue> filteredPValueArrayList = new ArrayList<MappedGeneSetTTestValue>();						
					
		    // Now we've got all the p values they need to be corrected. 	
			if (applyMultipleTestingCorrection) {
				BenjHochFDR.calculateQValues(filterResultpValues);
			}
			System.err.println(filterResultpValues.length + " p-values calculated, multtest = " + applyMultipleTestingCorrection + ", pval limit = " + pValueLimit); 				
			for (int i = 0; i < filterResultpValues.length; i++){
				
				double pOrQvalue;
				
				if(applyMultipleTestingCorrection){
					
					pOrQvalue = filterResultpValues[i].q;
				}
				else{
					
					pOrQvalue = filterResultpValues[i].p;
				}
				
				if(i <50 || filterResultpValues[i].mappedGeneSet.name().startsWith("MATURE B CELL") ){
					System.err.println("For " + filterResultpValues[i].mappedGeneSet.name() + ", pValue = " + filterResultpValues[i].p);
					System.err.println("For " + filterResultpValues[i].mappedGeneSet.name() + ", qValue = " + filterResultpValues[i].q);
				}
				
				if((pOrQvalue < pValueLimit) &&(Math.abs(filterResultpValues[i].mappedGeneSet.meanZScore) > zScoreThreshold)){
					
					filteredPValueArrayList.add(filterResultpValues[i]);						
				}
			}	
			
			filterResultpValues = filteredPValueArrayList.toArray(new MappedGeneSetTTestValue[0]);
			
			if(filterResultpValues.length == 0){
				JOptionPane.showMessageDialog(SeqMonkApplication.getInstance(), "No sets of genes were identified using the selected parameters, \ntry changing the gene sets or relaxing the p-value/z-score thresholds.", "No gene sets identified", JOptionPane.INFORMATION_MESSAGE);
				
			}
			
			else{
				geneSetDisplay = new GeneSetDisplay(dataCollection, listDescription(), fromStore, toStore, probes, probeZScoreLookupTable, filterResultpValues, 
						startingList, customRegressionValues, simpleRegression);
				geneSetDisplay.addWindowListener(this);				
			}
			// We don't want to save the probe list here, we're bringing up the intermediate display from which probe lists can be saved. 
			progressCancelled();
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	

	/* (non-Javadoc)
	 * @see uk.ac.babraham.SeqMonk.Filters.ProbeFilter#description()
	 */
	@Override
	public String description() {
		return "Filters on the intensity corrected statistical difference between stores";
	}

	/* This is a bit messy because this should start the filter running, but we have to either select the probe lists or the gene set file to use first
	 * then we can actually start it using startRunningFilter */
	public void runFilter () throws SeqMonkException {
		if (! isReady()) {
			throw new SeqMonkException("Filter is not ready to run");
		}
		
	/*	if(selectedProbeLists != null || validGeneSetFile){
			Thread t = new Thread(this);
			t.start();
		}
		
		else{
	*/		/* get selected probelists  */	
			if(optionsPanel.probeListRadioButton.isSelected()){
				ProbeList [] probeLists = ProbeListSelectorDialog.selectProbeLists();
				if (probeLists == null || probeLists.length == 0){
					
					JOptionPane.showMessageDialog(SeqMonkApplication.getInstance(), "No probelists were selected.",  "No probe lists selected", JOptionPane.INFORMATION_MESSAGE);
					progressCancelled();
					optionsChanged();
					return;
				}			
				selectedProbeLists = probeLists;
				startRunningFilter();
				//runFilter();
			}
			
			else if(optionsPanel.geneSetsFileRadioButton.isSelected()){
				
				GeneSetFileOptionsPanel geneSetFileOptionsPanel = new GeneSetFileOptionsPanel();
				geneSetFileOptionsPanel.addWindowListener(this);
						
			}
		//}	
		//Thread t = new Thread(this);
		//t.start();
	}
	
	public void startRunningFilter (){
		
		Thread t = new Thread(this);
		t.start();
	}
	
	/* (non-Javadoc)
	 * @see uk.ac.babraham.SeqMonk.Filters.ProbeFilter#getOptionsPanel()
	 */
	@Override
	public JPanel getOptionsPanel() {
		return optionsPanel;
	}

	/* (non-Javadoc)
	 * @see uk.ac.babraham.SeqMonk.Filters.ProbeFilter#hasOptionsPanel()
	 */
	@Override
	public boolean hasOptionsPanel() {
		return true;
	}

	/* (non-Javadoc)
	 * @see uk.ac.babraham.SeqMonk.Filters.ProbeFilter#isReady()
	 */
	@Override
	public boolean isReady() {

		if (fromStores.length == 1 && toStores.length == 1 && pValueLimit != null && fromStores[0]!=toStores[0]) {
			return true;
		}

		// Check for the special case of comparing just to ourselves
//		if (fromStores.length == 1 && toStores.length == 1 && pValueLimit != null && (validGeneSetFile == true || optionsPanel.probeListRadioButton.isSelected()) && fromStores[0]!=toStores[0]) {
	//		return true;
	//	}
		return false;
	}

	/* (non-Javadoc)
	 * @see uk.ac.babraham.SeqMonk.Filters.ProbeFilter#name()
	 */
	@Override
	public String name() {
		return "Gene Set Filter";
	}

	/* (non-Javadoc)
	 * @see uk.ac.babraham.SeqMonk.Filters.ProbeFilter#listDescription()
	 */
	@Override
	protected String listDescription() {
		StringBuffer b = new StringBuffer();

		b.append("Filter on probes in ");
		b.append(collection.probeSet().getActiveList().name());

		b.append(" where p-value when comparing ");

		b.append(fromStore.name());

		b.append(" to ");

		b.append(toStore.name());
		
		b.append(" was below ");

		b.append(pValueLimit);
		
		if (applyMultipleTestingCorrection) {
			b.append(" (multiple testing correction applied)");
		}
		
		b.append(" with a sample size of ");
		b.append(probesPerSet);
		b.append(" when constructing the control distributions");

		if(calculateCustomRegression){
			b.append(". A custom regression was calculated");
		}
		
		else if(calculateLinearRegression){
			b.append(". Linear regression was calculated");
		}
		
		
		b.append(". Minimum absolute z-score was ");
		b.append(zScoreThreshold);
		
		b.append(". Quantitation was ");
		if (collection.probeSet().currentQuantitation() == null) {
			b.append("not known.");
		}
		else {
			b.append(collection.probeSet().currentQuantitation());
		}
		
		return b.toString();
	}

	/* (non-Javadoc)
	 * @see uk.ac.babraham.SeqMonk.Filters.ProbeFilter#listName()
	 */
	@Override
	protected String listName() {
		StringBuffer b = new StringBuffer();

		b.append("Gene Sets p<");
		b.append(pValueLimit);
		return b.toString();
	}

	private class AverageIntensityComparator implements Comparator<Integer> {

		private DataStore d1;
		private DataStore d2;
		private Probe [] probes;

		public AverageIntensityComparator (DataStore d1, DataStore d2,Probe [] probes) {
			this.d1 = d1;
			this.d2 = d2;
			this.probes = probes;
		}

		public int compare(Integer i1, Integer i2) {
			try {
				
				// if the average of the points is the same, sort by the x or y values 
				if((d1.getValueForProbe(probes[i2])+d2.getValueForProbe(probes[i2])) == (d1.getValueForProbe(probes[i1])+d2.getValueForProbe(probes[i1]))){
					
					// if the x values are the same, sort by y value
					if(d1.getValueForProbe(probes[i2]) == d1.getValueForProbe(probes[i1])){
						return  Double.compare(d2.getValueForProbe(probes[i2]),d2.getValueForProbe(probes[i1]));
					}
					// else sort by x value
					return Double.compare(d1.getValueForProbe(probes[i2]),d1.getValueForProbe(probes[i1]));		
				
				}				
				return Double.compare(d1.getValueForProbe(probes[i2])+d2.getValueForProbe(probes[i2]),d1.getValueForProbe(probes[i1])+d2.getValueForProbe(probes[i1]));
			} 
			catch (SeqMonkException e) {
				return 0;
			}
		}
	}

//	private class SingleComparison {
//
//		public int fromIndex;
//		public int toIndex;
//		public SingleComparison (int fromIndex, int toIndex) {
//			this.fromIndex = fromIndex;
//			this.toIndex = toIndex;
//		}
//	}

	/**
	 * The DifferencesOptionsPanel.
	 */
	private class DifferencesOptionsPanel extends JPanel implements ListSelectionListener, KeyListener, ItemListener {

		private JList fromDataList;
		private JList toDataList;
		private JTextField pValueField;
		private JTextField zScoreField;
		private JCheckBox multipleTestingBox;
//		private JCheckBox calculateLinearRegressionBox;
		private JCheckBox calculateCustomRegressionBox;
		private JTextField pointsToSampleField;		
		private JRadioButton geneSetsFileRadioButton;
		private JRadioButton probeListRadioButton;
		
		/**
		 * Instantiates a new differences options panel.
		 */
		public DifferencesOptionsPanel () {

			setLayout(new BorderLayout());
			JPanel dataPanel = new JPanel();
			dataPanel.setBorder(BorderFactory.createEmptyBorder(4,4,0,4));
			dataPanel.setLayout(new GridBagLayout());
			GridBagConstraints dpgbc = new GridBagConstraints();
			dpgbc.gridx=0;
			dpgbc.gridy=0;
			dpgbc.weightx=0.5;
			dpgbc.weighty=0.01;
			dpgbc.fill=GridBagConstraints.BOTH;

			dataPanel.add(new JLabel("From Data Store / Group",JLabel.CENTER),dpgbc);

			DefaultListModel fromDataModel = new DefaultListModel();			
			DefaultListModel toDataModel = new DefaultListModel();			
			
			DataStore [] stores = collection.getAllDataStores();
			for (int i=0;i<stores.length;i++) {
				if (stores[i].isQuantitated()) {
					fromDataModel.addElement(stores[i]);					
					toDataModel.addElement(stores[i]);					
				}
			}

			dpgbc.gridy++;
			dpgbc.weighty=0.99;

			fromDataList = new JList(fromDataModel);						
			fromDataList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			
			//ListDefaultSelector.selectDefaultStores(fromDataList);						
			fromStores=SeqMonkApplication.getInstance().drawnDataStores();
			fromDataList.setCellRenderer(new TypeColourRenderer());
			fromDataList.addListSelectionListener(this);
			
			dataPanel.add(new JScrollPane(fromDataList),dpgbc);

			dpgbc.gridy++;
			dpgbc.weighty=0.01;

			dataPanel.add(new JLabel("To Data Store / Group",JLabel.CENTER),dpgbc);

			dpgbc.gridy++;
			dpgbc.weighty=0.99;

			toDataList = new JList(fromDataModel);
			//ListDefaultSelector.selectDefaultStores(toDataList);
			toStores=SeqMonkApplication.getInstance().drawnDataStores();
			toDataList.setCellRenderer(new TypeColourRenderer());
			toDataList.addListSelectionListener(this);
			toDataList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			dataPanel.add(new JScrollPane(toDataList),dpgbc);

			add(dataPanel,BorderLayout.WEST);

			JPanel choicePanel = new JPanel();
			choicePanel.setLayout(new GridBagLayout());
			GridBagConstraints gbc = new GridBagConstraints();
			gbc.gridx=1;
			gbc.gridy=1;
			gbc.insets = new Insets(5, 5, 5, 5);
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.weightx=0.1;
			gbc.weighty=0.5;
			
			/** Add in z-score filter */
			
			choicePanel.add(new JLabel("P-value must be below "),gbc);
			pValueField = new JTextField(""+pValueLimit,5);
			pValueField.addKeyListener(this);
			pValueField.addKeyListener(new NumberKeyListener(true, false, 100));
			
			gbc.gridx = 2;
			gbc.weightx=0.9;
			choicePanel.add(pValueField,gbc);
			
			gbc.gridx = 1;
			gbc.weightx=0.1;
			gbc.gridy++;
			
			choicePanel.add(new JLabel("Average absolute z-score must be above "),gbc);
			zScoreField = new JTextField(""+zScoreThreshold);
			zScoreField.addKeyListener(this);
			zScoreField.addKeyListener(new NumberKeyListener(true, false));
			
			gbc.gridx = 2;
			gbc.weightx=0.9;
			choicePanel.add(zScoreField,gbc);
			
			gbc.gridx = 1;
			gbc.weightx=0.1;
			gbc.gridy++;			
			
			
			choicePanel.add(new JLabel("Apply Multiple Testing Correction"),gbc);
			
			gbc.gridx=2;
			gbc.weightx=0.9;
			
			multipleTestingBox = new JCheckBox();
			multipleTestingBox.setSelected(true);
			choicePanel.add(multipleTestingBox,gbc);
			
			// removing the linear regression option for now, don't delete the code though.
		/**	gbc.gridx = 1;
			gbc.weightx=0.1;
			gbc.gridy++;
			
			choicePanel.add(new JLabel("Calculate Linear Regression"),gbc);
			
			gbc.gridx=2;
			gbc.weightx=0.9;
			
			calculateLinearRegressionBox = new JCheckBox();
			calculateLinearRegressionBox.setSelected(false);
			choicePanel.add(calculateLinearRegressionBox,gbc);
		*/	
			gbc.gridx = 1;
			gbc.weightx=0.1;
			gbc.gridy++;
			
			choicePanel.add(new JLabel("Calculate Custom distribution"),gbc);
			
			gbc.gridx=2;
			gbc.weightx=0.9;
			
			calculateCustomRegressionBox = new JCheckBox();
			calculateCustomRegressionBox.setSelected(false);
			choicePanel.add(calculateCustomRegressionBox,gbc);
						
			gbc.gridx = 1;
			gbc.weightx=0.1;
			gbc.gridy++;
			
			choicePanel.add(new JLabel("Number of probes per sample"),gbc);
			
			gbc.gridx=2;
			gbc.weightx=0.9;
			
			pointsToSampleField = new JTextField(""+probesPerSet,5);
			pointsToSampleField.addKeyListener(new NumberKeyListener(false, false,startingList.getAllProbes().length/2));
			choicePanel.add(pointsToSampleField,gbc);
			
			gbc.gridx = 1;
			gbc.weightx=0.1;
			gbc.gridy++;
				
			
			choicePanel.add(new JLabel("Use gene sets from file"),gbc);
			
			gbc.gridx=2;	
			gbc.weightx=0.9;
			geneSetsFileRadioButton = new JRadioButton();
			geneSetsFileRadioButton.setSelected(true);

			choicePanel.add(geneSetsFileRadioButton, gbc);
			
			gbc.gridy++;
					
			gbc.gridx=1;
			gbc.weightx=0.1;
			gbc.gridy++;
			choicePanel.add(new JLabel("Use existing probe lists"),gbc);
						
			probeListRadioButton = new JRadioButton();
			
			gbc.gridx=2;
			gbc.weightx=0.9;
			choicePanel.add(probeListRadioButton, gbc);
		
			ButtonGroup group = new ButtonGroup();
			group.add(geneSetsFileRadioButton);
			group.add(probeListRadioButton);		
			
			add(new JScrollPane(choicePanel),BorderLayout.CENTER);

		}	
												
		public Integer probesPerSet () {
			if (pointsToSampleField.getText().trim().length() == 0) {
				return null;
			}
			
			return Integer.parseInt(pointsToSampleField.getText());
		}
		
		/* (non-Javadoc)
		 * @see javax.swing.JComponent#getPreferredSize()
		 */
		public Dimension getPreferredSize () {
			return new Dimension(700,500);
		}

		/* (non-Javadoc)
		 * @see java.awt.event.KeyListener#keyTyped(java.awt.event.KeyEvent)
		 */
		public void keyTyped(KeyEvent arg0) {
		}

		/* (non-Javadoc)
		 * @see java.awt.event.KeyListener#keyPressed(java.awt.event.KeyEvent)
		 */
		public void keyPressed(KeyEvent ke) {

		}

		/* (non-Javadoc)
		 * @see java.awt.event.KeyListener#keyReleased(java.awt.event.KeyEvent)
		 */
		public void keyReleased(KeyEvent ke) {
			JTextField f = (JTextField)ke.getSource();

			Double d = null;

			if (f.getText().length()>0) {

				if (f.getText().equals("-")) {
					d = 0d;
				}
				else {
					try {
						d = Double.parseDouble(f.getText());
					}
					catch (NumberFormatException e) {
						f.setText(f.getText().substring(0,f.getText().length()-1));
						return;
					}
				}
			}

			if (f == pValueField) {
				pValueLimit = d;
				System.err.println("adjusting the p value to " + d);
			}
			else if(f == zScoreField){
				if(d == null){
					zScoreThreshold = (double)0; 
				}
				else{
					zScoreThreshold = d;					
				}
				System.err.println("adjusting the z-score threshold to " + zScoreThreshold);
			}
					
			else {
				System.err.println("Unexpected text field "+f+" sending data to keylistener in differences filter");
			}
			optionsChanged();

		}

		/* (non-Javadoc)
		 * @see java.awt.event.ItemListener#itemStateChanged(java.awt.event.ItemEvent)
		 */
		public void itemStateChanged(ItemEvent ie) {
		}

		/* (non-Javadoc)
		 * @see javax.swing.event.ListSelectionListener#valueChanged(javax.swing.event.ListSelectionEvent)
		 */
		public void valueChanged(ListSelectionEvent lse) {
			// If we have 2 or less items selected then we can disable the
			// combobox which says whether we're looking at min, max or average
			// difference (since they're all the same with only 1 comparison)

			Object [] fromSelectedObjects = fromDataList.getSelectedValues();
			Object [] toSelectedObjects = toDataList.getSelectedValues();


			DataStore [] newFromStores = new DataStore[fromSelectedObjects.length];
			for (int i=0;i<fromSelectedObjects.length;i++){
				newFromStores[i] = (DataStore)fromSelectedObjects[i];
			}
			fromStores = newFromStores;

			DataStore [] newToStores = new DataStore[toSelectedObjects.length];
			for (int i=0;i<toSelectedObjects.length;i++){
				newToStores[i] = (DataStore)toSelectedObjects[i];
			}
			toStores = newToStores;
			
			optionsChanged();
		}
		
	}
	
	private class GeneSetFileOptionsPanel extends JDialog implements KeyListener, ActionListener {
		
		// select gene set file
		private JButton selectFileButton; 
		private JButton closeButton; 
		private JTextField minGenesInCategory;
		private JTextField maxGenesInCategory;
		private JTextField geneSetFileLocation;
		
		public GeneSetFileOptionsPanel(){
			
			super(SeqMonkApplication.getInstance(),"Select gene set file");
			
			//setBorder(BorderFactory.createEmptyBorder(4,4,0,4));
			setLayout(new BorderLayout());
			//setLayout(
			
			setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			
			JPanel mainPanel = new JPanel();
			mainPanel.setBorder(BorderFactory.createEmptyBorder(4,4,0,4));
			mainPanel.setLayout(new GridBagLayout());
			GridBagConstraints gbc = new GridBagConstraints();
			gbc.gridx=0;
			gbc.gridy=0;
			gbc.weightx=0.5;
			gbc.weighty=0.5;
			gbc.fill=GridBagConstraints.HORIZONTAL;
			
						
			JPanel fileSelectPanel = new JPanel();
			fileSelectPanel.setLayout(new BoxLayout(fileSelectPanel, BoxLayout.X_AXIS));
			
			geneSetFileLocation = new JTextField("            ");
			if(validGeneSetFilepath != null){
				geneSetFileLocation.setText(validGeneSetFilepath);
			}			
			geneSetFileLocation.setEditable(false);
			fileSelectPanel.add(geneSetFileLocation);						
			
			fileSelectPanel.add(Box.createRigidArea(new Dimension(5,0)));
			
			selectFileButton = new JButton("Select file");
			selectFileButton.addActionListener(this);
			selectFileButton.setActionCommand("select_file");
			
			fileSelectPanel.add(selectFileButton);	
			
			mainPanel.add(fileSelectPanel, gbc);
			
			gbc.gridy++;
			gbc.gridy++;
			
			mainPanel.add(new JLabel("Number of genes in category"), gbc);		
			
			gbc.gridy++;
			
			JPanel minMaxPanel = new JPanel();
			minMaxPanel.setLayout(new BoxLayout(minMaxPanel, BoxLayout.X_AXIS));
			
			minMaxPanel.add(new JLabel("Minimum "));
			
			minGenesInCategory = new JTextField(""+minGenesInSet);			
			minGenesInCategory.addKeyListener(new NumberKeyListener(false, false));
			minGenesInCategory.addKeyListener(this);
			minMaxPanel.add(minGenesInCategory);
			
			minMaxPanel.add(new JLabel("  Maximum "));
			
			maxGenesInCategory = new JTextField(""+maxGenesInSet);
			maxGenesInCategory.addKeyListener(new NumberKeyListener(false, false));
			maxGenesInCategory.addKeyListener(this);
			minMaxPanel.add(maxGenesInCategory);	
			
			mainPanel.add(minMaxPanel, gbc);
						
			/* button panel at the bottom */
			JPanel buttonPanel =  new JPanel();
			buttonPanel.setLayout(new GridBagLayout());
			GridBagConstraints c2 = new GridBagConstraints();
			c2.insets = new Insets(2,2,2,2);
			c2.gridx=0;
			c2.gridy=0;
	
			closeButton = new JButton("Close");
			closeButton.addActionListener(this);
			closeButton.setActionCommand("close");
			buttonPanel.add(closeButton, c2);
			
			c2.gridx++;
			
			selectFileButton = new JButton("OK");
			selectFileButton.addActionListener(this);
			selectFileButton.setActionCommand("run");
			buttonPanel.add(selectFileButton, c2);	
			
			
			
			add(mainPanel, BorderLayout.CENTER);
			add(buttonPanel, BorderLayout.PAGE_END);	
			
			setSize(300, 150);
			setLocationRelativeTo(optionsPanel);
			setVisible(true);
		}
	
		public void actionPerformed(ActionEvent ae) {
			String action = ae.getActionCommand();
			
			if (action.equals("select_file")) {
				
				JFileChooser fc = new JFileChooser();				
				//fc.setCurrentDirectory(new File("D:/"));					
				fc.setCurrentDirectory(SeqMonkPreferences.getInstance().getDataLocation());
				
				fc.showOpenDialog(this);	
				
				if(fc.getSelectedFile() == null){
					validGeneSetFilepath = null;
					//validGeneSetFile = false;
					return; // they cancelled
				}
				else{				
					File selectedFile = fc.getSelectedFile();		
					String filepath = selectedFile.toString();	
					if ((filepath != null) && (fileValid(filepath))){
						//validGeneSetFile = true;
						validGeneSetFilepath = filepath;
						geneSetFileLocation.setText(validGeneSetFilepath);
						//this.dispose();
							//runFilter();
						//	startRunningFilter();						
					}
					else{
						validGeneSetFilepath = null;
						//validGeneSetFile = false;
					}
				}			
			}
			else if(ae.getActionCommand().equals("run")){
				
				if(minGenesInSet > maxGenesInSet){
					JOptionPane.showMessageDialog(SeqMonkApplication.getInstance(), "Minimum number of genes in set cannot be greater than maximum.", "Number of genes needs adjusting", JOptionPane.ERROR_MESSAGE);
					return;
				}		
				//else if(validGeneSetFile == false){
				else if(validGeneSetFilepath == null){
					JOptionPane.showMessageDialog(SeqMonkApplication.getInstance(), "A valid gene set file needs to be selected.", "Gene set file required", JOptionPane.ERROR_MESSAGE);
					return;
				}
				else{
					this.dispose();
					//runFilter();
					startRunningFilter();	
				}				
			}
			
			else if(ae.getActionCommand().equals("close")) {
				
				//validGeneSetFile = false;
				validGeneSetFilepath = null;
				progressCancelled();
				optionsChanged();
				this.dispose();
				
			}
			
		}
			// check whether the gene set info file can be found
		private boolean fileValid(String filepath){
			
			File f = new File(filepath);
			if(f.exists() && !f.isDirectory()) {
				return true;
			}
			else{
				JOptionPane.showMessageDialog(SeqMonkApplication.getInstance(), "The gene set information file couldn't be found, please find a valid file to load.", "Couldn't load gene set file", JOptionPane.ERROR_MESSAGE);
				return false;
			}
		}	
			
		public void keyPressed(KeyEvent arg0) {
			// TODO Auto-generated method stub
			
		}

		public void keyReleased(KeyEvent kr) {
			
			JTextField f = (JTextField)kr.getSource();

			Integer i = null;			
			
			if (f.getText().length()>0) {

				if (f.getText().equals("-")) {
					System.err.println("we've got " + f.getText());
				}
				else {
					try {
						i = Integer.parseInt(f.getText());
					}
					catch (NumberFormatException e) {
						f.setText(f.getText().substring(0,f.getText().length()-1));
						return;
					}
				}
			}
			if(f == minGenesInCategory){
				// bit messy
				if(i == null || (i==0)){
					minGenesInSet = 1;
				}	
			/*	if(i > maxGenesInSet){
					JOptionPane.showMessageDialog(SeqMonkApplication.getInstance(), "Minimum number cannot be greater than maximum", "Number of genes needs adjusting", JOptionPane.ERROR_MESSAGE);
				}
			*/	else{
					minGenesInSet = i;
				}
			}
			else if(f == maxGenesInCategory){
				if(i == null  || (i==0)){
					maxGenesInSet = 100000;
				}	 						
			/*	if(x < minGenesInSet){
					JOptionPane.showMessageDialog(SeqMonkApplication.getInstance(), "Maximum number cannot be less than minimum", "Number of genes needs adjusting", JOptionPane.ERROR_MESSAGE);
				}
			*/	else{
					maxGenesInSet = i;
				}
			}			
		}

		public void keyTyped(KeyEvent arg0) {
			// TODO Auto-generated method stub
			
		}
	}

	public void windowActivated(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void windowClosed(WindowEvent e) {
				
		if(e.getSource() instanceof GeneSetDisplay){
			
			System.err.println("not doing it from the filter");
			
			
		/*	GeneSetDisplay display = (GeneSetDisplay)(e.getSource());
			if(display.currentSelectedProbeList != null){
				if(display.currentSelectedProbeList[0] != null){
					display.currentSelectedProbeList[0].delete();
					display.currentSelectedProbeList = null;
				}	
			}			
	*/	}
				
	/*	if(e.getSource().equals(geneSetDisplay)){
			//System.err.println("yes, it's a geneSetDisplay");							
			//System.err.println("trying to delete probelist...");
			if(geneSetDisplay != null && geneSetDisplay.currentSelectedProbeList != null){
			
				geneSetDisplay.currentSelectedProbeList[0].delete();
				geneSetDisplay.currentSelectedProbeList = null;					
			}		
			selectedProbeLists = null;				
		}		
	*/	progressCancelled();
		// TODO Auto-generated method stub
		
	}

	public void windowClosing(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void windowDeactivated(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void windowDeiconified(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void windowIconified(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void windowOpened(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}
}	