package inf.unibz.it.obda.dependencies.miner;

import inf.unibz.it.obda.api.controller.APIController;
import inf.unibz.it.obda.api.datasource.JDBCConnectionManager;
import inf.unibz.it.obda.dependencies.miner.exception.MiningException;
import inf.unibz.it.obda.domain.DataSource;
import inf.unibz.it.obda.domain.OBDAMappingAxiom;
import inf.unibz.it.obda.domain.TargetQuery;
import inf.unibz.it.obda.gui.swing.datasource.panels.IncrementalResultSetTableModel;
import inf.unibz.it.obda.gui.swing.datasource.panels.ResultSetTableModel;
import inf.unibz.it.obda.gui.swing.datasource.panels.ResultSetTableModelFactory;
import inf.unibz.it.obda.rdbmsgav.domain.RDBMSSQLQuery;
import inf.unibz.it.ucq.domain.ConjunctiveQuery;
import inf.unibz.it.ucq.domain.ConstantTerm;
import inf.unibz.it.ucq.domain.FunctionTerm;
import inf.unibz.it.ucq.domain.QueryAtom;
import inf.unibz.it.ucq.domain.QueryTerm;
import inf.unibz.it.ucq.domain.VariableTerm;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;

import javax.swing.JOptionPane;

public class RDBMSFunctionalDependencyMiner implements IMiner {

	/**
	 *A collection of all mappings  
	 */
	private Collection<OBDAMappingAxiom> mappings;
	/**
	 * Indicates how many threads should be used to do the containment checking. 
	 */
	private int nrOfThreads = 2;
	/**
	 * This map contains for each SQL query executed by the threads the result as 
	 * a boolean Object. Using this map we avoid executing the same query several
	 * times. 
	 */
	private Map<String, Boolean> resultOfSQLQueries =null;
	/**
	 * A list of jobs, which has to be executed by the threads. The length of the
	 * object array can either be 3 or 4. If the length is 3 it contains at position
	 * 0 the SQL query to execute and at position 1 and 2 the TermMappings for which
	 * the check whether the TermMapping in position 1 is contained in the one at 
	 * position 2.
	 * If the array length is 4 again at position 0 one can find the sql query. At
	 * Position 1 and 2 the two atoms and at pos 3 the index of the term one which
	 * the check is done. 
	 */
	private List<Job> queue = null;
	/**
	 * The data source manager is used to identify the used DBMS on which it 
	 * depends how the sql queries are produced.
	 */
	private CountDownLatch doneSignal = null;
	/**
	 * A logger for doing a logfile, which contains information about possible
	 * errors or exceptions
	 */
//	private Logger log = LoggerFactory.getLogger(InclusionDependencyMiner.class);
	
	/**
	 * A set of found mining results, which later will be tranformed in
	 * inclusion dependencies
	 */
	private HashSet<FunctionalDependencyMiningResult> foundInclusion = null;
	
	/**
	 * The API controller
	 */
	private APIController apic = null;
	
	/**
	 * A set of all target queries in the mappings
	 */
	private HashSet<QueryAtom> splitted = null;
	
	/**
	 * A map which show from which mapping each target query comes from
	 */
	private HashMap<QueryAtom, OBDAMappingAxiom> sourceQueryMap= null;
	
	/**
	 * A list of threads, which are execution the mining
	 */
	private List<MiningThread> threads = null;
	
	private boolean hasErrorOccurred = false;
	
	private MiningException exception = null;
	/**
	 * Creates a new instance of the RDBMSInclusionDependencyMiner
	 * 
	 * @param apic the API controller
	 * @param signal a count down latch
	 */
	public RDBMSFunctionalDependencyMiner(APIController apic,
			CountDownLatch signal){	
		
		this.apic = apic;
		resultOfSQLQueries = new HashMap<String, Boolean>();
		mappings = apic.getMappingController().getMappings(apic.getDatasourcesController().getCurrentDataSource().getName());
		queue = new Vector<Job>();
		sourceQueryMap = new HashMap<QueryAtom, OBDAMappingAxiom>();
		foundInclusion = new HashSet<FunctionalDependencyMiningResult>();
		doneSignal = signal;
		createJobs();
	}
	
	/**
	 * The method goes through and tries to find possible inclusion
	 * dependencies. When it finds a two candidates it creates 
	 * a job object and adds it to the queue. Later the job will
	 * be executed to whether it actually is inclusion dependency or not
	 */
	private void createJobs(){
		
		Iterator<OBDAMappingAxiom> it = mappings.iterator();
		while(it.hasNext()){
			OBDAMappingAxiom axiom = it.next();
			HashSet<QueryTerm> terms = getTerms(axiom);
			HashSet<QueryTerm> candidates = findCandidates(axiom);
			Iterator<QueryTerm> can_it = candidates.iterator();
			while(can_it.hasNext()){
				QueryTerm candidate = can_it.next();
				String sql = produceSQL(axiom.getSourceQuery().getInputQuString(), candidate, terms);
				Job aux = new Job(sql, axiom.getId(), (RDBMSSQLQuery)axiom.getSourceQuery(), candidate, terms);
				queue.add(aux);
			}
		}
	}
	
	private HashSet<QueryTerm> getTerms(OBDAMappingAxiom ax){
		ConjunctiveQuery q = (ConjunctiveQuery) ax.getTargetQuery();
		ArrayList<QueryAtom> atoms = q.getAtoms();
		Iterator<QueryAtom> it = atoms.iterator();
		HashSet<QueryTerm> aux = new HashSet<QueryTerm>();
		while(it.hasNext()){
			QueryAtom atom = it.next();
			ArrayList<QueryTerm> terms =atom.getTerms();
			Iterator<QueryTerm> t_it = terms.iterator();
			while(t_it.hasNext()){
				QueryTerm term = t_it.next();
				if(!(term instanceof ConstantTerm)){
					aux.add(term);
				}
			}
		}
		return aux;
	}
	
	private HashSet<QueryTerm> findCandidates(OBDAMappingAxiom ax){
		ConjunctiveQuery q = (ConjunctiveQuery) ax.getTargetQuery();
		ArrayList<QueryAtom> atoms = q.getAtoms();
		Iterator<QueryAtom> it = atoms.iterator();
		HashSet<QueryTerm> candidates = new HashSet<QueryTerm>();
		while(it.hasNext()){
			QueryAtom atom = it.next();
			ArrayList<QueryTerm> terms =atom.getTerms();
			Iterator<QueryTerm> t_it = terms.iterator();
			while(t_it.hasNext()){
				QueryTerm term = t_it.next();
				if(term instanceof FunctionTerm){
					candidates.add(term);
				}
			}
		}
		return candidates;
	}
	
	/**
	 * The method takes the queue of jobs, divides them in to equal parts
	 * and passes them to different threads, which executes the jobs.
	 */
	public void startMining(){
		
		threads = new Vector<MiningThread>();
		int qLength = queue.size()/nrOfThreads;
		Random random = new Random();
		// makes n-1 threads
		for(int i=0; i<nrOfThreads-1; i++){
			Vector<Job> auxQueue = new Vector<Job>();
			for (int j=0; j<qLength; j++){
				int index = random.nextInt(queue.size());
				auxQueue.add(queue.get(index));
				queue.remove(index);
			}
			MiningThread thread = new MiningThread(auxQueue, doneSignal);
			threads.add(thread);
			thread.start();
		}
		// makes the n-th thread an assigns it all remaining jobs
		MiningThread thread = new MiningThread(queue,doneSignal);
		threads.add(thread);
		thread.start();
	}
	
	/**
	 * Interrupts all started Mining threads
	 */
	public void cancelMining(){
		Iterator<MiningThread> it= threads.iterator();
		while(it.hasNext()){
			it.next().interrupt();
		}
		foundInclusion = new HashSet<FunctionalDependencyMiningResult>();
	}	
	
	/**
	 * Returns the query which can be use to check the dependency on the
	 * data in the source if the involved terms are functional terms.
	 */
	private String produceSQL(String sql, QueryTerm candidate, Set<QueryTerm> terms){
		
		FunctionTerm ft = (FunctionTerm) candidate;
		ArrayList<QueryTerm> vars = ft.getParameters();
		Iterator<QueryTerm> it = vars.iterator();
		String selection = "";
		String clause ="";
		while(it.hasNext()){
			if(selection.length() > 0){
				selection = selection + ", ";
				clause = clause + " AND ";
			} 
			String name = it.next().getName();
			selection = selection + "alias1."+name;
			clause = clause + "alias1." + name + " = alias2."+ name+ " ";
		}
		String query = "SELECT " + selection +" FROM (" + sql +") alias1 , (" + sql +") alias2 WHERE " +clause;
		
		String test = "";
		Iterator<QueryTerm> it1 = terms.iterator();
		while(it1.hasNext()){
			QueryTerm qt = it1.next();
			if(!qt.equals(candidate)){
				if(qt instanceof VariableTerm){
					VariableTerm vt = (VariableTerm) qt;
					String name = vt.getName();
					if(test.length() > 0){
						test = test + " OR "; 
					}
					test = test + "alias1." + name + " <> alias2."+ name+ " ";
				}else if(qt instanceof FunctionTerm){
					FunctionTerm t = (FunctionTerm) qt;
					ArrayList<QueryTerm> list = t.getParameters();
					Iterator<QueryTerm> l_it = list.iterator();
					while(l_it.hasNext()){
						QueryTerm aux = l_it.next();
						String name = aux.getName();
						if(test.length() > 0){
							test = test + " OR "; 
						}
						test = test + "alias1." + name + " <> alias2."+ name+ " ";
					}
				}
			}
		}
		
		query = query +" AND ( "+test+")"; 
		return query; 
	}
	
	/**
	 * Returns the results of the mining
	 * @return set of mining results
	 */
	public HashSet<FunctionalDependencyMiningResult> getFoundInclusionDependencies() {
		return foundInclusion;
	}

	public MiningException getException() {
		return exception;
	}

	@Override
	public boolean hasErrorOccurred() {
		return hasErrorOccurred;
	}
	
	/**
	 * Class representing a mining result. It contains all
	 * necessary information to create inclusion dependency, which
	 * can be used by the reasoner.
	 * 
	 * @author Manfred Gerstgrasser
 * 		   KRDB Research Center, Free University of Bolzano/Bozen, Italy 
	 *
	 */
	public class FunctionalDependencyMiningResult{
		
		/**
		 * The frist query
		 */
		private RDBMSSQLQuery sourceQuery = null;
		/**
		 * the query term associated to the first query
		 */
		private QueryTerm dependent = null;
		/**
		 * the query term associated to the second query
		 */
		private Set<QueryTerm> dependee = null;
		/**
		 * the mapping id of the mapping where the first query comes from
		 */
		private String mappingId = null;

		
		/**
		 * Return a new MiningResult object
		 * 
		 * @param id1 first mapping id
		 * @param id2 second mapping id
		 * @param map1	first query
		 * @param pos1	term associated to the first query
		 * @param map2	second query
		 * @param pos2	term associated to the second query
		 */
		private FunctionalDependencyMiningResult(String id, RDBMSSQLQuery map, QueryTerm can, 
				Set<QueryTerm> terms){
			
			mappingId = id;
			sourceQuery = map;
			dependent = can;
			dependee = terms;
		}


		public RDBMSSQLQuery getSourceQuery() {
			return sourceQuery;
		}


		public QueryTerm getDependent() {
			return dependent;
		}


		public Set<QueryTerm> getDependee() {
			return dependee;
		}


		public String getMappingId() {
			return mappingId;
		}
		
		
	}
	
	/**
	 * Class representing a job, which will be executed by a Mining Thread
	 * 
	 * @author Manfred Gerstgrasser
	 * 		   KRDB Research Center, Free University of Bolzano/Bozen, Italy 
	 *
	 */
	private class Job {
		
		/**
		 * the query to execute
		 */
		private String sqlquery = null;
		/**
		 * the second query
		 */
		private RDBMSSQLQuery sourceQuery = null;
		/**
		 * the term associated to the first query
		 */
		private QueryTerm candidateTerm = null;
		/**
		 * the term associated to the second query
		 */
		private Set<QueryTerm> dependentTerms = null;
		/**
		 * the id of the mapping where the first query comes from
		 */
		private String mappingID =null;
		
		/**
		 * Returns a new Job object
		 * 
		 * @param sql	the query to execute
		 * @param id1	id of first mapping
		 * @param id2	id of second mapping
		 * @param can	the first query
		 * @param posCan term associated to first query
		 * @param con	the second query
		 * @param posCon	term associated to second query
		 */
		private Job(String sql, String id, RDBMSSQLQuery can,QueryTerm posCan,
				Set<QueryTerm> dep){
			
			sqlquery = sql;
			candidateTerm = posCan;
			mappingID = id;
			sourceQuery = can;
			dependentTerms = dep;
		}

		/**
		 * Returns the query to execute
		 * @return query to execute
		 */
		public String getSqlquery() {
			return sqlquery;
		}

		public RDBMSSQLQuery getSourceQuery() {
			return sourceQuery;
		}

		public QueryTerm getCandidateTerm() {
			return candidateTerm;
		}

		public Set<QueryTerm> getDependentTerms() {
			return dependentTerms;
		}

		public String getMappingID() {
			return mappingID;
		}
		
		
	}
//		
//		
//
//	// This class extends the class java.lang.Thread and executes the jobs assigned to it
//	// by executing the sql query, checking the result and if necessary it inserts or
//	// updating an entry in the corresponding index
	private class MiningThread extends Thread{
		
		/**
		 * The list of jobs the threads has to do.
		 */
		private List<Job> jobs = null;
		/**
		 * The count down signal 
		 */
		private final CountDownLatch signal;
		/**
		 * the result set model factory
		 */
		private JDBCConnectionManager	man	= null;
		/**
		 * the result set model
		 */
		private IncrementalResultSetTableModel	model			= null;
		
		/**
		 * returns a new Mining Thread
		 * @param obj list of jobs to execute
		 * @param latch the count down signal
		 */
		private MiningThread (List<Job> obj, CountDownLatch latch){
			jobs = obj;
			this.signal = latch;
		}
		
		@Override
		public void run(){
			JDBCConnectionManager man = JDBCConnectionManager.getJDBCConnectionManager();
			
			try {
				man.setProperty(JDBCConnectionManager.JDBC_AUTOCOMMIT, true);
				man.setProperty(JDBCConnectionManager.JDBC_RESULTSETTYPE, ResultSet.TYPE_SCROLL_SENSITIVE);
				Iterator<Job> it = jobs.iterator();
				while(it.hasNext()){
					Job job = it.next();
					String sql = job.getSqlquery(); //get the sql query
					synchronized(resultOfSQLQueries){
						if(resultOfSQLQueries.containsKey(sql)){
							Boolean aux = resultOfSQLQueries.get(sql);// check whether the query was already executed
							if(aux.booleanValue()){// if so and the result had a containment, it is added to the entry of this query
								
								FunctionalDependencyMiningResult entry = new FunctionalDependencyMiningResult(job.getMappingID(), job.getSourceQuery(), job.getCandidateTerm(), job.getDependentTerms());
								synchronized(foundInclusion){
									foundInclusion.add(entry);
								}
							}
						}else{// otherwise the query gets executed and the result is added to the map
								try {
									DataSource ds = apic.getDatasourcesController().getCurrentDataSource();
									if(!man.isConnectionAlive(ds.getUri())){
										man.createConnection(ds);
									}
//									if (model != null) {
//
//										IncrementalResultSetTableModel rstm = (IncrementalResultSetTableModel) model;
//										rstm.close();
//									}
									ResultSet set =  man.executeQuery(ds.getUri(), sql, ds);
									model = new IncrementalResultSetTableModel(set);
									synchronized(resultOfSQLQueries){
									if(model.getRowCount() != 0){
										resultOfSQLQueries.put(sql, new Boolean("false"));
									}else{
										FunctionalDependencyMiningResult entry = new FunctionalDependencyMiningResult(job.getMappingID(), job.getSourceQuery(), job.getCandidateTerm(), job.getDependentTerms());
										synchronized(foundInclusion){
											foundInclusion.add(entry);
										}
										resultOfSQLQueries.put(sql, new Boolean("true"));
									}
								}
							} catch (Exception e) {
								resultOfSQLQueries.put(sql, new Boolean("false"));
							} 
						}
					}
				}
				signal.countDown();//notify the Latch that the thread has finished
//				log.debug("Thead ended");
			} catch (Exception e) {
				signal.countDown();
				hasErrorOccurred = true;
				exception = new MiningException("Excetpion thrown during mining process.\n" + e.getMessage());
//			}
		}
	}
}
}