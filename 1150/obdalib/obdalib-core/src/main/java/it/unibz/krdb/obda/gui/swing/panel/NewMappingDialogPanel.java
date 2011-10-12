/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */
package it.unibz.krdb.obda.gui.swing.panel;

import it.unibz.krdb.obda.exception.DuplicateMappingException;
import it.unibz.krdb.obda.gui.swing.utils.DatasourceSelectorListener;
import it.unibz.krdb.obda.gui.swing.utils.MappingStyledDocument;
import it.unibz.krdb.obda.model.CQIE;
import it.unibz.krdb.obda.model.DatalogProgram;
import it.unibz.krdb.obda.model.OBDADataFactory;
import it.unibz.krdb.obda.model.OBDADataSource;
import it.unibz.krdb.obda.model.OBDAModel;
import it.unibz.krdb.obda.model.OBDARDBMappingAxiom;
import it.unibz.krdb.obda.model.OBDASQLQuery;
import it.unibz.krdb.obda.model.impl.OBDADataFactoryImpl;
import it.unibz.krdb.obda.parser.DatalogProgramParser;
import it.unibz.krdb.obda.parser.DatalogQueryHelper;
import it.unibz.krdb.obda.utils.OBDAPreferences;
import it.unibz.krdb.obda.utils.TargetQueryValidator;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.net.URI;
import java.util.Vector;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.StyleContext;

import org.antlr.runtime.RecognitionException;
import org.semanticweb.owl.model.OWLOntology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * NewMappingDialogPanel.java
 * 
 * Created on 11-apr-2011, 17.39.34
 */

/**
 * 
 * @author obda
 */
public class NewMappingDialogPanel extends javax.swing.JPanel implements DatasourceSelectorListener {

	private static final long		serialVersionUID	= 4351696247473906680L;

	/** Fields */
	private OBDAModel				controller			= null;
	private OBDAPreferences			preferences			= null;
	private OBDADataSource			dataSource			= null;
	private JDialog					parent				= null;
	private TargetQueryValidator	validator			= null;

	private DatalogProgramParser	datalogParser		= new DatalogProgramParser();
	private OBDADataFactory			dataFactory			= OBDADataFactoryImpl.getInstance();

	/** Logger */
	private final Logger			log					= LoggerFactory.getLogger(this.getClass());

	/**
	 * Create the dialog for inserting a new mapping.
	 * 
	 * @param controller
	 * @param preference
	 * @param parent
	 * @param dataSource
	 * @param ontology
	 */
	public NewMappingDialogPanel(OBDAModel controller, OBDAPreferences preference, JDialog parent, OBDADataSource dataSource,
			OWLOntology ontology) {
		this.controller = controller;
		this.preferences = preference;
		this.parent = parent;
		this.dataSource = dataSource;

		validator = new TargetQueryValidator(ontology);

		initComponents();
		init();
	}

	private void init() {

		final MappingStyledDocument mapdoc = new MappingStyledDocument(new StyleContext(), controller, preferences);
		txtTargetQuery.setDocument(mapdoc);

		cmdInsertMapping.setEnabled(false);
		cmdInsertMapping.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				final String targetQueryString = txtTargetQuery.getText().trim();
				final String sourceQueryString = txtSourceQuery.getText().trim();

				if (!targetQueryString.isEmpty() && !sourceQueryString.isEmpty()) {
					insertMapping(targetQueryString, sourceQueryString);
				} else {
					JOptionPane.showMessageDialog(null, "The query string can't be empty!", "Error", JOptionPane.ERROR_MESSAGE);
				}
			}
		});

		ActionListener actionListenerCancel = new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				parent.setVisible(false);
				parent.dispose();
			}
		};
		KeyStroke ks_ecape = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
		this.registerKeyboardAction(actionListenerCancel, ks_ecape, JComponent.WHEN_IN_FOCUSED_WINDOW);

		ActionListener actionListenerAccept = new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				final String targetQueryString = txtTargetQuery.getText().trim();
				final String sourceQueryString = txtSourceQuery.getText().trim();

				if (!targetQueryString.isEmpty() && !sourceQueryString.isEmpty()) {
					insertMapping(targetQueryString, sourceQueryString);
				} else {
					JOptionPane.showMessageDialog(null, "The query string can't be empty!", "Error", JOptionPane.ERROR_MESSAGE);
				}
			}
		};
		KeyStroke ks_enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK);
		this.registerKeyboardAction(actionListenerAccept, ks_enter, JComponent.WHEN_IN_FOCUSED_WINDOW);

		mapdoc.addDocumentListener(new DocumentListener() {

			@Override
			public void removeUpdate(DocumentEvent arg0) {
				// TODO Auto-generated method stub

			}

			@Override
			public void insertUpdate(DocumentEvent arg0) {
				// TODO Auto-generated method stub

			}

			@Override
			public void changedUpdate(DocumentEvent arg0) {
				Runnable action = new Runnable() {

					@Override
					public void run() {
						try {
							Thread.currentThread().sleep(10);
						} catch (InterruptedException e) {
						}

						cmdInsertMapping.setEnabled(mapdoc.isValidQuery());
					}
				};
				SwingUtilities.invokeLater(action);

			}
		});

		cmdCancel.setMnemonic('c');
		cmdTestQuery.setMnemonic('t');
		cmdInsertMapping.setMnemonic('i');
	}

	private void insertMapping(String target, String source) {
		CQIE targetQuery = parse(target);
		if (targetQuery != null) {
			final boolean isValid = validator.validate(targetQuery);
			if (isValid) {
				try {
					OBDAModel mapcon = controller;
					URI sourceID = dataSource.getSourceID();

					/* Computing an ID for the new mapping */

					int index = 0;
					for (int i = 0; i < 99999999; i++) {
						index = mapcon.indexOf(sourceID, "M:" + Integer.toHexString(i));
						if (index == -1) {
							index = i;
							break;
						}
					}
					String id = "M:" + Integer.toHexString(index);

					OBDASQLQuery body = dataFactory.getSQLQuery(source);
					OBDARDBMappingAxiom mapping = dataFactory.getRDBMSMappingAxiom(id, body, targetQuery);
					mapcon.addMapping(sourceID, mapping);
				} catch (DuplicateMappingException e) {
					JOptionPane.showMessageDialog(null, "Error while inserting mapping.\n " + e.getMessage()
							+ "\nPlease refer to the log file for more information.");
					log.error("Error while counting tuples.", e);
				}
				parent.setVisible(false);
				parent.dispose();
			} else {
				// List of invalid predicates that are found by the validator.
				Vector<String> invalidPredicates = validator.getInvalidPredicates();
				String invalidList = "";
				for (String predicate : invalidPredicates) {
					invalidList += "- " + predicate + "\n";
				}
				JOptionPane.showMessageDialog(null, "This list of predicates is unknown by the ontology: \n" + invalidList, "New Mapping",
						JOptionPane.WARNING_MESSAGE);
			}
		}
	}

	/**
	 * This method is called from within the constructor to initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is always
	 * regenerated by the Form Editor.
	 */
	@SuppressWarnings("unchecked")
	// <editor-fold defaultstate="collapsed"
	// <editor-fold defaultstate="collapsed"
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        lblTargetQuery = new javax.swing.JLabel();
        lblSourceQuery = new javax.swing.JLabel();
        scrTargetQuery = new javax.swing.JScrollPane();
        txtTargetQuery = new javax.swing.JTextPane();
        scrSourceQuery = new javax.swing.JScrollPane();
        txtSourceQuery = new javax.swing.JTextPane();
        cmdTestQuery = new javax.swing.JButton();
        pnlCommandButton = new javax.swing.JPanel();
        cmdInsertMapping = new javax.swing.JButton();
        cmdCancel = new javax.swing.JButton();

        setBorder(javax.swing.BorderFactory.createTitledBorder("Create Mapping"));
        setMinimumSize(new java.awt.Dimension(400, 300));
        setPreferredSize(new java.awt.Dimension(400, 300));
        setLayout(new java.awt.GridBagLayout());

        lblTargetQuery.setText("Target Query:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        add(lblTargetQuery, gridBagConstraints);

        lblSourceQuery.setText("Source Query:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(7, 4, 4, 4);
        add(lblSourceQuery, gridBagConstraints);

        txtTargetQuery.setToolTipText("Write the query that will be the head of the mapping. \\nThis is a conjunctive query, possibly with function simbols to create object uris from the data of the databse. \\n For example: obdap:q($id) :- Person(individual-uri($id))");
        txtTargetQuery.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                changeTargetQueryFocus(evt);
            }
        });
        scrTargetQuery.setViewportView(txtTargetQuery);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        add(scrTargetQuery, gridBagConstraints);

        txtSourceQuery.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                changeSourceQueryFocus(evt);
            }
        });
        scrSourceQuery.setViewportView(txtSourceQuery);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        add(scrSourceQuery, gridBagConstraints);

        cmdTestQuery.setText("Test SQL Query");
        cmdTestQuery.setActionCommand("Test SQL query");
        cmdTestQuery.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmdTestQueryActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        add(cmdTestQuery, gridBagConstraints);

        pnlCommandButton.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));

        cmdInsertMapping.setText("Insert Mapping");
        pnlCommandButton.add(cmdInsertMapping);

        cmdCancel.setText("Cancel");
        cmdCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmdCancelActionPerformed(evt);
            }
        });
        pnlCommandButton.add(cmdCancel);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        add(pnlCommandButton, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents

private void changeSourceQueryFocus(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_changeSourceQueryFocus
    if(evt.getKeyCode() == KeyEvent.VK_TAB) {
        if(evt.getModifiers() > 0) {
            txtSourceQuery.transferFocusBackward();
        }
        else {
            txtSourceQuery.transferFocus();
        }
        evt.consume();
    }
}//GEN-LAST:event_changeSourceQueryFocus

private void changeTargetQueryFocus(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_changeTargetQueryFocus
    if(evt.getKeyCode() == KeyEvent.VK_TAB) {
        if(evt.getModifiers() > 0) {
            txtTargetQuery.transferFocusBackward();
        }
        else {
            txtTargetQuery.transferFocus();
        }
        evt.consume();
    }
}//GEN-LAST:event_changeTargetQueryFocus

	private void cmdTestQueryActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_jButtonTestActionPerformed
		final JDialog resultquery = new JDialog();
		resultquery.setModal(true);
		SQLQueryPanel query_panel = new SQLQueryPanel(dataSource, txtSourceQuery.getText());

		JPanel panel = new JPanel();
		panel.setLayout(new GridBagLayout());
		GridBagConstraints gridBagConstraints = new java.awt.GridBagConstraints();
		gridBagConstraints.gridx = 0;
		gridBagConstraints.gridy = 0;
		gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
		gridBagConstraints.weightx = 1.0;
		gridBagConstraints.weighty = 1.0;
		gridBagConstraints.insets = new Insets(5, 5, 5, 5);
		panel.add(query_panel, gridBagConstraints);

		resultquery.setContentPane(panel);
		resultquery.pack();
		resultquery.setLocationRelativeTo(null);
		resultquery.setVisible(true);
		resultquery.setTitle("Query Results");
	}// GEN-LAST:event_jButtonTestActionPerformed

	private void cmdCancelActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_jButtonCancelActionPerformed
		parent.setVisible(false);
		parent.dispose();
	}// GEN-LAST:event_jButtonCancelActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cmdCancel;
    private javax.swing.JButton cmdInsertMapping;
    private javax.swing.JButton cmdTestQuery;
    private javax.swing.JLabel lblSourceQuery;
    private javax.swing.JLabel lblTargetQuery;
    private javax.swing.JPanel pnlCommandButton;
    private javax.swing.JScrollPane scrSourceQuery;
    private javax.swing.JScrollPane scrTargetQuery;
    private javax.swing.JTextPane txtSourceQuery;
    private javax.swing.JTextPane txtTargetQuery;
    // End of variables declaration//GEN-END:variables

	private CQIE parse(String query) {
		CQIE cq = null;
		try {
			String input = prepareQuery(query);
			DatalogProgram dp = datalogParser.parse(input);
			if (dp.getRules().size() > 0) {
				cq = dp.getRules().get(0);  // TODO Change this when the system supports multiple data sources.
			}
		} catch (RecognitionException e) {
			log.warn(e.getMessage());
		}
		return cq;
	}

	private String prepareQuery(String input) {
		String query = "";
		DatalogQueryHelper queryHelper = new DatalogQueryHelper(controller.getPrefixManager());

		String[] atoms = input.split(DatalogQueryHelper.DATALOG_IMPLY_SYMBOL, 2);
		if (atoms.length == 1) // if no head
			input = queryHelper.getDefaultHead() + " " + DatalogQueryHelper.DATALOG_IMPLY_SYMBOL + " " + input;

		query += queryHelper.getPrefixes() + input;

		return query;
	}

	@Override
	public void datasourceChanged(OBDADataSource oldSource, OBDADataSource newSource) {
		dataSource = newSource;
	}
}
