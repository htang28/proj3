package org.ucb.act.synthesis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.ucb.act.synthesis.model.Cascade;
import org.ucb.act.synthesis.model.Reaction;
import org.ucb.act.synthesis.model.Traceback;
import org.ucb.act.utils.FileUtils;

/**
 *
 * @author J. Christopher Anderson
 */
public class Synthesizer2 {
    //Holds all the reactions and chemicals
    private final List<Reaction> reactions = new ArrayList<>();
    private final Map<Long, String> chemIdToInchi = new HashMap<>();
    private final Map<Long, String> chemIdToName = new HashMap<>();
    
    //Variables for holding state of the expansion
    private int currshell = 0;
    private final Map<Long, Integer> chemicalToShell = new HashMap<>();
    private final Map<Reaction, Integer> reactionToShell = new HashMap<>();
    
    /**
     * Read in all the Reactions from file
     * 
     * @throws Exception 
     */
    public void populateReactions(String rxnpath) throws Exception {
        //Parse the Reactions from file and add to the arraylist
        String rxndata = FileUtils.readFile(rxnpath);
        rxndata = rxndata.replaceAll("\"", "");
        String[] lines = rxndata.trim().split("\\r|\\r?\\n");
        rxndata = null; //No longer need data, clear out memory
        
        //Iterate through lines, one line has one reaction after a header line
        for(int i=1; i<lines.length; i++) {
            String aline = lines[i];
            try {
                //Pull out the data for one reaction
                String[] tabs = aline.trim().split("\t");
                Long id = Long.parseLong(tabs[0]);
                String substrates = tabs[2];
                String products = tabs[3];
                Set<Long> subs = handleChemIdList(substrates);
                Set<Long> pdts = handleChemIdList(products);
                
                //Instantiate the reaction, then add it
                Reaction rxn = new Reaction(id, subs, pdts);
                reactions.add(rxn);
            } catch(Exception err) {
                throw err;
            }
        }
        
        System.out.println("done populating reactions" );
    }

    /**
     * Helper method for populateReactions
     * 
     * Parses the serialized reference to the list of substrates or products
     * 
     * @param chemidstring
     * @return
     * @throws Exception 
     */
    private Set<Long> handleChemIdList(String chemidstring) throws Exception {
        Set<Long> out = new HashSet<>();
        String[] stringids = chemidstring.trim().split("\\s");
        for (int i = 0; i < stringids.length; i++) {
            Long id = Long.parseLong(stringids[i]);
            out.add(id);
        }
        return out;
    }
    
    public void populateChemicals(String chempath) throws Exception {
        //Read in all the chemicals
        String chemdata = FileUtils.readFile(chempath);
        chemdata = chemdata.replaceAll("\"", "");
        String[] lines = chemdata.trim().split("\\r|\\r?\\n");
        chemdata = null;
        
        //Each line of the file is a chemical after a header
        for(int i=1; i<lines.length; i++) {
            String aline = lines[i];
            String[] tabs = aline.trim().split("\t");
            Long id = Long.parseLong(tabs[0]);
            String inchi = tabs[2];
            String name = tabs[1];
            
            //All we need for our algorithm is the inchi and name for output
            chemIdToInchi.put(id, inchi);
            chemIdToName.put(id, name);
        }
        
        System.out.println("done populating chemicals" );
    }
    
    public void populateNatives(String path) throws Exception {        
        //Populate a HashSet to hold all native E. coli inchis
        Set<String> nativeInchis = new HashSet<>();
        
        //Read the inchis from file
        String nativedata = FileUtils.readFile(path);
        nativedata = nativedata.replaceAll("\"", "");
        String[] lines = nativedata.trim().split("\\r|\\r?\\n");
        nativedata = null;
        
        //Each line of the file is a chemical, add it to the list
        for(int i=1; i<lines.length; i++) {
            String aline = lines[i];
            String[] tabs = aline.split("\t");
            String inchi = tabs[1];
            nativeInchis.add(inchi);
        }
        
        //For each chemical in reactions, see if it is a native
        for(Long id : chemIdToInchi.keySet()) {
            String inchi = chemIdToInchi.get(id);
            if(nativeInchis.contains(inchi)) {
                chemicalToShell.put(id, 0);
            }
        }
        
        System.out.println("done populating natives: " + path  + ", have: " + chemicalToShell.size() + " reachables");
    }

    /**
     * One round of wavefront expansion.  It iterates through all the reactions
     * and if the substrates for that reaction are enabled, then it enables the
     * products of the reaction.  If any reactions are added during a round of
     * expansion, the method returns true.
     * 
     * @return returns true if new reactions were added to the expansion
     * @throws Exception 
     */
    public boolean ExpandOnce() throws Exception {
        //Increment the current shell
        currshell++;
        
        boolean isExpanded = false;
        
        //Iterate through reactions
        outer: for(Reaction rxn : reactions) {
            //If the reaction has already been put in the expansion, skip this reaction
            if(reactionToShell.containsKey(rxn)) {
                continue;
            }
            
            //If any of the substates are not enabled, skip this reaction
            for(Long chemid : rxn.getSubstrates()) {
                Integer shell = chemicalToShell.get(chemid);
                if(shell==null) {
                    continue outer;
                }
            }
            
            //If gets this far, the Reaction is enabled and new, thus expansion will occur
            isExpanded = true;
            
            //Log the reaction into the expansion at the current shell
            reactionToShell.put(rxn, currshell);
            
            //For each product, enable it with current shell (if it isn't already)
            for(Long chemid : rxn.getProducts()) {
                Integer shell = chemicalToShell.get(chemid);
                if(shell==null) {
                    chemicalToShell.put(chemid, currshell);
                }
            }
        }
        
        System.out.println("Expanded shell: " + currshell + " result " + isExpanded + " with " + chemicalToShell.size() + " reachables");
        return isExpanded;
    }
    
    public void printReachables() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("id\tname\tinchi\tshell\n");
        for(Long chemid : chemicalToShell.keySet()) {
            String inchi = chemIdToInchi.get(chemid);
            String name = chemIdToName.get(chemid);
            sb.append(chemid).append("\t").append(name).append("\t").append(inchi).append("\t").append(chemicalToShell.get(chemid)).append("\n");
        }
        FileUtils.writeFile(sb.toString(), "metacyc_L2_reachables.txt");
    }
    
    
    private Traceback calculateTraceback() {
        //Create a Map to associate the Reachable chemIds to the reactions that form them
        Map<Long, Set<Reaction>> chemPdtToReactions = new HashMap<>();

        //Iterate through all the enabled Reactions
        for(Reaction rxn : reactionToShell.keySet()) {
            
            //Index this rxn for each product
            for(Long pdtId : rxn.getProducts()) {
                Set<Reaction> rxnSet = chemPdtToReactions.get(pdtId);
                if(rxnSet==null) {
                    rxnSet = new HashSet<>();
                }
                rxnSet.add(rxn);
                chemPdtToReactions.put(pdtId, rxnSet);
            }
        }
        
        return new Traceback(chemPdtToReactions, chemicalToShell);
    }
    
    public static void main(String[] args) throws Exception {
        Synthesizer2 synth = new Synthesizer2();
        
        //Populate the chemical and reaction data
        synth.populateReactions("good_reactions.txt");
        synth.populateChemicals("good_chems.txt");
        
        //To use non-inchi-validated version of MetaCyc, run these instead
//        synth.populateReactions("metacyc_reactions.txt");
//        synth.populateChemicals("metacyc_chemicals.txt");
        
        //Populate the bag of chemicals to consider as shell 0 "natives"
        synth.populateNatives("minimal_metabolites.txt");
        synth.populateNatives("universal_metabolites.txt");

        //Expand until exhausted
        while(synth.ExpandOnce()) {}
        
        //Print out the reachables and their shell
        synth.printReachables();
        
        //Calculate the Traceback and compute a Cascade
        Traceback traceback = synth.calculateTraceback();
        Cascade cascade = traceback.getCascade(24709l);
        
        System.out.println("done");
    }

}