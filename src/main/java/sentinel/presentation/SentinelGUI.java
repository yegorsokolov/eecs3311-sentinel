package sentinel.presentation;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.*;
import sentinel.application.*;
import sentinel.domain.*;
import sentinel.domain.Action;
import sentinel.events.ProgressListener;
import sentinel.persistence.JsonCodec;
import sentinel.reporting.ReportService;
/** Swing view: every service action uses a SwingWorker; rendering and progress stay on the EDT. */
public final class SentinelGUI implements ProgressListener {
    private final AgentController controller;private final ActionDispatcher dispatcher;
    private JFrame frame;private final JTextArea progress=new JTextArea(3,60);private final Map<String,JTextArea> views=new LinkedHashMap<>();private final java.util.List<JButton> buttons=new ArrayList<>();
    private final JTextField request=new JTextField("Investigate the synthetic account takeover"),target=new JTextField("demo-analyst"),start=new JTextField("2026-10-22T12:00:00Z"),end=new JTextField("2026-10-22T13:00:00Z"),feed=new JTextField("demo-feed-v1"),question=new JTextField("What evidence supports this assessment?");
    private final JTextField args=new JTextField("{}"),critical=new JTextField();private final JComboBox<String> type=new JComboBox<>(new String[]{"ACCOUNT","INDICATOR"}),tool=new JComboBox<>(new String[]{"logSearch","userActivity","indicatorLookup"});
    private final JCheckBox rule=new JCheckBox("Use approved basic rule plan");private String dataset;private volatile UUID activeId;
    public SentinelGUI(AgentController controller){this.controller=controller;dispatcher=new ActionDispatcher(controller);}
    public Result submit(Action action){return dispatcher.submit(action);}
    public void show(String dataset,Runnable onClose){if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("GUI construction requires EDT");this.dataset=dataset;
        frame=new JFrame("Sentinel - incident investigation");((JPanel)frame.getContentPane()).setBorder(BorderFactory.createEmptyBorder(8,8,8,8));frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);frame.addWindowListener(new WindowAdapter(){public void windowClosed(WindowEvent e){onClose.run();}});
        var fields=new JPanel(new GridLayout(0,2,6,4));field(fields,"Request",request);field(fields,"Target",target);field(fields,"Target type",type);field(fields,"Start UTC",start);field(fields,"End UTC",end);field(fields,"Feed version",feed);fields.add(rule);fields.add(new JLabel("Dataset: "+dataset));field(fields,"Critical hosts (comma separated)",critical);
        var controls=new JPanel(new GridLayout(0,4,5,5));button(controls,"New case",()->{var a=scopeArgs();a.put("request",request.getText());a.put("type",type.getSelectedItem().toString());a.put("feed",feed.getText());a.put("rule",Boolean.toString(rule.isSelected()));perform("new",a);});
        button(controls,"Import JSON",()->choose("import","file",false));button(controls,"Generate model plan",()->perform("plan",Map.of()));button(controls,"Approve rule plan",()->perform("rule",Map.of()));button(controls,"Clarify scope",()->perform("clarify",scopeArgs()));button(controls,"Revise completed scope",()->perform("revise",scopeArgs()));
        button(controls,"Run",()->perform("run",critical.getText().isBlank()?Map.of():Map.of("criticalHosts",critical.getText())));var cancel=new JButton("Stop after current operation");cancel.addActionListener(e->{try{controller.cancel(activeId);}catch(RuntimeException ignored){}});controls.add(cancel);
        button(controls,"Revalidate paused plan",()->perform("resume",Map.of()));button(controls,"Save",()->perform("save",Map.of()));button(controls,"Open case",this::open);button(controls,"Export Markdown",()->choose("export","out",true));
        var top=new JPanel(new BorderLayout());top.add(fields,BorderLayout.NORTH);top.add(controls,BorderLayout.CENTER);
        var query=new JPanel(new BorderLayout(4,4));query.add(tool,BorderLayout.WEST);query.add(args,BorderLayout.CENTER);var queryButtons=new JPanel();button(queryButtons,"Fill scope arguments",this::fillArgs);button(queryButtons,"Query",()->perform("query",Map.of("tool",tool.getSelectedItem().toString(),"args",args.getText())));query.add(queryButtons,BorderLayout.SOUTH);top.add(query,BorderLayout.SOUTH);
        var tabs=new JTabbedPane();for(String name:ListNames()){var text=new JTextArea();text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);text.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));views.put(name,text);tabs.add(name,new JScrollPane(text));}
        var bottom=new JPanel(new BorderLayout());var ask=new JPanel(new BorderLayout());ask.add(question);var askButtons=new JPanel();button(askButtons,"Ask",()->perform("ask",Map.of("question",question.getText())));ask.add(askButtons,BorderLayout.EAST);bottom.add(ask,BorderLayout.NORTH);progress.setEditable(false);bottom.add(new JScrollPane(progress),BorderLayout.CENTER);
        frame.add(top,BorderLayout.NORTH);frame.add(tabs,BorderLayout.CENTER);frame.add(bottom,BorderLayout.SOUTH);frame.setSize(1150,850);frame.setLocationRelativeTo(null);frame.setVisible(true);
    }
    private java.util.List<String> ListNames(){return java.util.List.of("Plan","Evidence","Timeline","Hypotheses","Assessment","Conversation","History","Report");}
    private void field(JPanel p,String label,JComponent component){p.add(new JLabel(label));p.add(component);}
    private Map<String,String> scopeArgs(){var a=new HashMap<String,String>();a.put("dataset",dataset);a.put("version",currentVersion());a.put("target",target.getText());a.put("start",start.getText());a.put("end",end.getText());return a;}
    private String currentVersion(){try{return controller.current().scope().datasetVersion();}catch(RuntimeException ex){return "v0";}}
    private void button(JPanel p,String label,Runnable r){var b=new JButton(label);b.addActionListener(e->r.run());buttons.add(b);p.add(b);}
    private void choose(String command,String key,boolean save){var chooser=new JFileChooser();if((save?chooser.showSaveDialog(frame):chooser.showOpenDialog(frame))!=JFileChooser.APPROVE_OPTION)return;Path p=chooser.getSelectedFile().toPath();var a=new HashMap<String,String>();a.put(key,p.toString());if(save&&Files.exists(p)){if(JOptionPane.showConfirmDialog(frame,"Replace existing export?","Confirm overwrite",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;a.put("overwrite","true");}perform(command,a);}
    private void open(){
        buttons.forEach(b->b.setEnabled(false));
        new SwingWorker<java.util.List<CaseSummary>,Void>() {
            protected java.util.List<CaseSummary> doInBackground(){return controller.listCases();}
            protected void done(){try{var cases=get();if(cases.isEmpty()){progress.append("No saved cases.\n");return;}
                var options=cases.stream().map(c->c.id()+" | "+c.status()+" | "+c.goal()).toArray(String[]::new);
                String chosen=(String)JOptionPane.showInputDialog(frame,"Select a saved case","Open case",JOptionPane.PLAIN_MESSAGE,null,options,options[0]);
                if(chosen!=null)perform("open",Map.of("case",chosen.split(" ")[0]));
            }catch(Exception ex){progress.append("Cannot list saved cases.\n");}finally{buttons.forEach(b->b.setEnabled(true));}}
        }.execute();
    }
    private void fillArgs(){try{var s=controller.current();var a=new HashMap<String,String>();if(tool.getSelectedItem().equals("indicatorLookup")){a.put("indicator",s.targetType()==TargetType.INDICATOR?s.scope().target():"192.0.2.10");a.put("feedVersion",feed.getText());}else{a.put("datasetId",s.scope().datasetId());a.put("datasetVersion",s.scope().datasetVersion());a.put("start",s.scope().start().toString());a.put("end",s.scope().end().toString());a.put(s.targetType()==TargetType.ACCOUNT?"accountId":"sourceIp",s.scope().target());a.put("limit","1000");}args.setText(JsonCodec.encode(a));}catch(RuntimeException ex){progress.append("Create or open a scoped case first.\n");}}
    private void perform(String command,Map<String,String> a){buttons.forEach(b->b.setEnabled(false));new SwingWorker<CaseSnapshot,Void>(){
        protected CaseSnapshot doInBackground(){try{Result r=submit(new Action(command,a));update(new ProgressEvent("ACTION",r.status(),java.util.List.of()));}catch(RuntimeException ex){update(new ProgressEvent("ERROR",ex instanceof sentinel.llm.ModelException m?m.code().name():"Action failed; check scope, input, state or model availability",java.util.List.of()));}try{return controller.current();}catch(RuntimeException ex){return null;}}
        protected void done(){try{var s=get();if(s!=null)render(s);}catch(Exception ex){progress.append("Worker failed\n");}finally{buttons.forEach(b->b.setEnabled(true));}}
    }.execute();}
    private void render(CaseSnapshot s){activeId=s.id();request.setText(s.goal());type.setSelectedItem(s.targetType().name());feed.setText(s.feedVersion()==null?"":s.feedVersion());if(s.scope()!=null){target.setText(s.scope().target());start.setText(s.scope().start().toString());end.setText(s.scope().end().toString());}frame.setTitle("Sentinel - "+s.id()+" - "+s.status());var a=s.assessment();views.get("Plan").setText(JsonCodec.pretty(s.plan()));views.get("Evidence").setText(s.evidence().stream().map(JsonCodec::encode).collect(java.util.stream.Collectors.joining("\n\n")));views.get("Timeline").setText(a==null?"Not assessed":JsonCodec.pretty(a.timeline())+"\n\nRelated pairs (not causation):\n"+JsonCodec.pretty(new sentinel.analysis.TimelineBuilder().correlate(a.timeline())));views.get("Hypotheses").setText(a==null?"Not assessed":JsonCodec.pretty(a.hypotheses()));views.get("Assessment").setText(a==null?"Not assessed":JsonCodec.pretty(a.severity())+"\nCompleteness: "+a.completeness()+"\n"+JsonCodec.pretty(a.recommendations()));views.get("Conversation").setText(JsonCodec.pretty(s.conversation()));views.get("History").setText("Case: "+s.id()+"\n"+JsonCodec.pretty(s.history()));views.get("Report").setText(new ReportService().preview(s));views.values().forEach(v->v.setCaretPosition(0));}
    public void update(ProgressEvent e){SwingUtilities.invokeLater(()->progress.append(e.stage()+": "+e.outcome()+"\n"));}
}
