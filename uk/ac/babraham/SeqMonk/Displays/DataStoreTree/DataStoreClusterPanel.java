package uk.ac.babraham.SeqMonk.Displays.DataStoreTree;

import java.awt.Color;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.JPanel;

import uk.ac.babraham.SeqMonk.DataTypes.DataStore;
import uk.ac.babraham.SeqMonk.DataTypes.Cluster.ClusterPair;

public class DataStoreClusterPanel extends JPanel {
	
	private static final int LEFT_BORDER = 10;
	private static int RIGHT_BORDER = 1;
	
	private ClusterPair clusterSet;
	private DataStore [] stores;
	private float [] rRange;
	
	// This value is the proprtion through the r range at which we're 
	// currently segmenting the tree
	private float rProportion = 0;
	
	public DataStoreClusterPanel (ClusterPair clusterSet, DataStore [] stores) {
		this.clusterSet = clusterSet;
		this.stores = stores;
		
		// We also need to know the range of r values we can see
		rRange = new float[]{clusterSet.rValue(),clusterSet.rValue()};
		getRRange(clusterSet, rRange);
		
		// Leave some space for the perfectly correlated starting positions - we don't
		// insist on these being displayed as 1 as it will mess up the scaling for the
		// rest of the plot.
		rRange[1]+=(rRange[1]-rRange[0])/20;
		
		if (rRange[0] == rRange[1]) {
			rRange[1] += 0.1;
			rRange[0] -= 0.2;
		}
		if (rRange[0] < -1) rRange[0] = -1;
		if (rRange[1] > 1) rRange[1] = 1;
		
		System.err.println("Range is "+rRange[0]+" to "+rRange[1]);

	}
	
	public void setRProportion (float proportion) {
		rProportion = proportion;
		repaint();
	}
	
	
	public void paint (Graphics g) {
		super.paint(g);
		
		if (RIGHT_BORDER == 1 || RIGHT_BORDER > getWidth()/2) {
			// Work out how much space we actually need on the right
			for (int s=0;s<stores.length;s++) {
				int width = g.getFontMetrics().stringWidth(stores[s].name())+10;
				if (width > getWidth()/2) width = getWidth()/2;
				if (width > RIGHT_BORDER) RIGHT_BORDER = width;
			}
		}
		
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, getWidth(), getHeight());
		g.setColor(Color.BLACK);
		
		// First get the set of indices which are going to dictate the final order
		Integer [] indices = clusterSet.getAllIndices();
		
		// We now need to work our way backwards through the cluster pairs to
		// get the join positions for each of the groups.
		
		// We first need to get the starting cluster for each index
		
		HashMap<Integer, ClusterPair> initialPairs = new HashMap<Integer, ClusterPair>();
		
		getInitialPairs(clusterSet,initialPairs);
		
		// Now we need to build up a data structure which maps the x and y positions 
		// of drawn nodes to the cluster pair they came from.
		
		HashMap<ClusterPair, xyPosition> xyPositions = new HashMap<ClusterPair, DataStoreClusterPanel.xyPosition>();
		
		// We populate this initially with the cluster pairs we've just found in the 
		// order we were given overall.
		
		for (int i=0;i<indices.length;i++) {
			if (!initialPairs.containsKey(indices[i])) {
				throw new IllegalStateException("No starting position found for index "+indices[i]);
			}
			xyPosition newPosition = new xyPosition(getX(rRange[1]), (getHeight()*(i+1))/(indices.length+1));
			xyPositions.put(initialPairs.get(indices[i]), newPosition);
			
			g.drawString(stores[indices[i]].name(), newPosition.x+5,newPosition.y+(g.getFontMetrics().getAscent()/2));
		}
		
		
		// Now we can keep moving back and joining up the pairs we find.
		
		while (xyPositions.size() > 1) {
			plotClusters(g,clusterSet,xyPositions,rRange);
		}
		
		
		// Now draw the line to show where we're segmenting
		if (rProportion > 0) {
			g.drawLine(getX(getRSplit()), 0, getX(getRSplit()), getHeight());
		}
		
	}
	
	public float getRSplit () {
		float rSplit = rRange[0]+ ((rRange[1]-rRange[0])*rProportion);
		
		return rSplit;
	}
	
	public DataStore [][] getSplitStores (int limitPerStore) {

		ClusterPair [] splitClusterPairs = clusterSet.getConnectedClusters(getRSplit());
		
		ArrayList<DataStore[]> keepers = new ArrayList<DataStore[]>();
		
		for (int i=0;i<splitClusterPairs.length;i++) {
			Integer [] indices = splitClusterPairs[i].getAllIndices();
			
			if (indices.length < limitPerStore) continue;
			
			DataStore [] clusterStores = new DataStore[indices.length];
			for (int s=0;s<indices.length;s++) {
				clusterStores[s] = stores[indices[s]]; 
			}
				
			keepers.add(clusterStores);
		
		}
		
		return keepers.toArray(new DataStore[0][]);
		
	}
	
	public DataStore [] getOrderedStores () {
		Integer [] indices = clusterSet.getAllIndices();
		
		DataStore [] orderedStores = new DataStore[indices.length];
		
		for (int i=0;i<indices.length;i++) {
			orderedStores[i] = stores[indices[i]];
		}
		
		return orderedStores;
	}
	
	
	private int getX (float r) {
		
		float proportion = (r-rRange[0])/(rRange[1]-rRange[0]);
		
//		System.err.println("Range ="+range[0]+" to "+range[1]+" r="+r+" propotion="+proportion);
		
		return LEFT_BORDER+(int)((getWidth()-(LEFT_BORDER+RIGHT_BORDER))*proportion);
				
	}
	
	private void plotClusters (Graphics g, ClusterPair pair, HashMap<ClusterPair, xyPosition> positions,float [] range) {
				
		if (pair.pair1() == null) return;
		
		// We need to see if both of the clusters under this pair are in the positions file
		if (positions.containsKey(pair.pair1()) && positions.containsKey(pair.pair2())) {
			
			// We can draw the linkage between these clusters.
			
//			System.err.println("Found a match with R="+pair.rValue());
			
			int y = (positions.get(pair.pair1()).y+positions.get(pair.pair2()).y)/2;
			int x = getX(pair.rValue());
			
			// We don't allow negative branches
			if (x > positions.get(pair.pair1()).x) x = positions.get(pair.pair1()).x;			
			if (x > positions.get(pair.pair2()).x) x = positions.get(pair.pair2()).x;
			
			// Draw the bar between them
			g.drawLine(positions.get(pair.pair1()).x, positions.get(pair.pair1()).y, x, positions.get(pair.pair1()).y);
			g.drawLine(x, positions.get(pair.pair1()).y, x, positions.get(pair.pair2()).y);
			g.drawLine(positions.get(pair.pair2()).x, positions.get(pair.pair2()).y, x, positions.get(pair.pair2()).y);
			
			// Remove the old clusters from the data
			positions.remove(pair.pair1());
			positions.remove(pair.pair2());
			
			// Add the new cluster
			positions.put(pair,new xyPosition(x, y));
		}
		else {
			plotClusters(g, pair.pair1(), positions,range);
			plotClusters(g, pair.pair2(), positions,range);
		}
		
	}
	
	
	private void getInitialPairs(ClusterPair pair, HashMap<Integer,ClusterPair>data) {
		
		if (pair.index() != null) {
			data.put(pair.index(),pair);
		}
		
		else {
			getInitialPairs(pair.pair1(), data);
			getInitialPairs(pair.pair2(), data);
		}
	}

	private void getRRange(ClusterPair pair, float [] range) {
		
		if (pair.index() != null) return;
		
		if (pair.rValue()<range[0]) range[0] = pair.rValue();
		if (pair.rValue()>range[1]) range[1] = pair.rValue();		
		
		getRRange(pair.pair1(), range);
		getRRange(pair.pair2(), range);
		
	}

	
	
	private class xyPosition {
		
		public int x;
		public int y;
		
		public xyPosition(int x, int y) {
			this.x = x;
			this.y = y;
		}
	}
	
}
