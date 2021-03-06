package Backend;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;

// Exporter class from backend data structure to json for front end
public class Exporter {

    public List<Tuple> interfaces;
    public List<Tuple> classes;
    private List<Tuple> abstractClasses;
    private Hashtable<String, Integer> ids;
    private int count;
    private Map<Integer, Boolean> allowed;
    private Map<Integer, Integer> occurences;
    // TODO check sources and target fields for existance in the written nodes

    public Exporter(Map<String, InterfaceObj> interfaces, Map<String, ClassObj> classes, Map<String, ClassObj> absClasses) {

        this.interfaces = new ArrayList<>();
        this.classes = new ArrayList<>();
        this.abstractClasses = new ArrayList<>();
        allowed = new HashMap<>();
        ids = new Hashtable<>();
        occurences = new HashMap<>();
        count = 1;

        for (Map.Entry<String, InterfaceObj> entry: interfaces.entrySet()) {
            try {
                this.interfaces.add(new Tuple(entry.getKey(), entry.getValue()));
                String k = entry.getKey();
                occurences.put(ids.get(k), 0);
            } catch (Exception b) {
                b.printStackTrace();
            }

        }
        for(Map.Entry<String, ClassObj> entry: classes.entrySet()){
            try {
                this.classes.add(new Tuple(entry.getKey(), entry.getValue()));
                String k = entry.getKey();
                occurences.put(ids.get(k), 0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        for(Map.Entry<String, ClassObj> entry: absClasses.entrySet()) {
            try {
                this.abstractClasses.add(new Tuple(entry.getKey(), entry.getValue()));
                occurences.put(ids.get(entry.getKey()), 0);
            }catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    public void writeToJson() {
        try {
            final String[] directory = {"graph"};
            EventQueue.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    JFileChooser fileChooser = new JFileChooser();
                    fileChooser.setDialogTitle("Select save copy location");
                    int good = fileChooser.showSaveDialog(null);
                    if (good == JFileChooser.APPROVE_OPTION) {
                        File file = fileChooser.getSelectedFile();
                        directory[0] = file.getAbsolutePath();
                    } else {
                        System.out.println("Save error");
                    }
                }
            });
            try {
                write(directory[0]);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void write(String directory) throws IOException {
        JSONObject outer = new JSONObject();
        JSONArray nodes = new JSONArray();
        JSONArray links = new JSONArray();
        writeNodes(nodes);
        getDependencies(interfaces);
        getDependencies(classes);
        getDependencies(abstractClasses);
        writeLinks(links);
        writeDependencies(nodes);


        outer.put("nodes", nodes);
        outer.put("links",links);
        FileWriter f = new FileWriter(new File(directory+".json"));
        outer.writeJSONString(f);
        f.close();
//        File output = new File("Frontend UI/graph.json");
//        if (output.exists()) {
//            output.delete();
//        }
//        FileWriter defaultForVisualWriter = new FileWriter(output);
//        outer.writeJSONString(defaultForVisualWriter);
//        defaultForVisualWriter.close();
    }

    private void writeDependencies(JSONArray nodes) {
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject n = (JSONObject) nodes.get(i);
            n.put("dependencies", occurences.get(n.get("id")));
        }
    }

    private void getDependencies(List<Tuple> t) {
        for (Tuple tup: t) {
            try {
                if (tup.value.fields != null) {
                    for (Map.Entry<ClassObj, Integer> s : tup.value.fields.entrySet()) {
                        if(allowed.containsKey(tup.id) && allowed.containsKey(ids.get(s.getKey().name))) {
                            int i = occurences.get(tup.id);
                            i += 1;
                            occurences.put(tup.id, i);
                        }
                    }
                }
                if (tup.value.interfaces != null) {
                    for (InterfaceObj in : tup.value.interfaces) {
                        if (in != null) {
                            if(allowed.containsKey(tup.id) || allowed.containsKey(ids.get(in.name))) {
                                int i = occurences.get(tup.id);
                                i += 1;
                                occurences.put(tup.id, i);
                            }
                        }
                    }
                }
                if (tup.value.superClass != null) {
                    if(allowed.containsKey(tup.id) || allowed.containsKey(ids.get(tup.value.superClass.name))) {
                        int i = occurences.get(tup.id);
                        i += 1;
                        occurences.put(tup.id, i);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void writeLinks(JSONArray links) {
        writeLinksHelper(links, interfaces);
        writeLinksHelper(links, classes);
        writeLinksHelper(links, abstractClasses);
    }

    private void writeLinksHelper(JSONArray links, List<Tuple> t) {
        for(Tuple tup: t) {
            try {
                // field dependencies
                if (tup.value.fields != null) {
                    for (Map.Entry<ClassObj, Integer> s : tup.value.fields.entrySet()) {
                        if (ids.get(s.getKey().name) != null) {
                            writeLink(links, tup.id, ids.get(s.getKey().name), "field");
                        }
                    }
                }
                // interface dependencies
                if (tup.value.interfaces.size() != 0) {
                    for (InterfaceObj in : tup.value.interfaces) {
                        if (in != null) {
                            if (ids.get(in.name) != null) {
                                writeLink(links, tup.id, ids.get(in.name), "implements");
                            }
                        }
                    }
                }
                // superclass dependencies
                if (tup.value.superClass != null) {
                    // todo what about recursive inheritance?
                    if (ids.get(tup.value.superClass.name) != null) {
                        writeLink(links, tup.id, ids.get(tup.value.superClass.name), "extends");
                    }
                }
                // static call dependencies
                if (tup.value.staticCalls != null) {
                    for(ClassObj c: tup.value.staticCalls) {
                        try {
                            writeLink(links,tup.id,ids.get(c.name), "static call");
                        }catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void writeLink(JSONArray links, int src, int dest, String type) {
        if(src == dest) {
            return;
        }

        if(allowed.containsKey(src) && allowed.containsKey(dest)) {
            JSONObject ob = new JSONObject();
            ob.put("source", src);
            ob.put("target", dest);
            ob.put("type", type);
            links.add(ob);
        }
    }

    private void writeNodes(JSONArray nodes) { // todo refactor to method
        // writing interface nodes
        nodeWriterHelper(nodes,interfaces,"interface");
        // writing classes nodes
        nodeWriterHelper(nodes,classes,"class");
        // writing abstract class nodes
        nodeWriterHelper(nodes,abstractClasses,"abstract class");
    }

    private void nodeWriterHelper(JSONArray nodes,List<Tuple> collection,String type) {
        for(Tuple t: collection) {
            try {
                JSONObject ob = new JSONObject();
                ob.put("name", t.value.name);
                ob.put("label", type);
                ob.put("id", t.id);
                ob.put("dependencies", occurences.get(ids.get(t.key)));
                ob.put("numMethods", t.value.methods.size());
                nodes.add(ob);
                allowed.put(t.id, true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private int hashClass(String in) {
        if (!ids.contains(in)) {
            ids.put(in, count);
            count++;
        }
        return ids.get(in);
    }

    private class Tuple {
        public String key;
        public ClassObj value;
        public int id;
        public Tuple(String key, ClassObj value){
            this.key = key;
            this.value = value;
            this.id = hashClass(key);
        }
    }

}


