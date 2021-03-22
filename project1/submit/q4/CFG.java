import java.util.Set;
import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Stack;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

public class CFG {
	
    Set<Node> nodes = new HashSet<Node>();
    Map<Node, Set<Node>> edges = new HashMap<Node, Set<Node>>();

    static class Node {

		int position;
		MethodNode method;
		ClassNode clazz;

		Node(int p, MethodNode m, ClassNode c) {
			position = p; method = m; clazz = c;
		}

		public boolean equals(Object o) {
			if (!(o instanceof Node)) return false;
			Node n = (Node)o;
			return (position == n.position) &&
			method.equals(n.method) && clazz.equals(n.clazz);
		}

		public int hashCode() {
			return position + method.hashCode() + clazz.hashCode();
		}

		public String toString() {
			return clazz.name + "." +
			method.name + method.signature + ": " + position;
		}
    }

    public void addNode(int p, MethodNode m, ClassNode c) {
	// ...	
		Node newNode = new Node(p, m, c);
		boolean check = true;

		for(Node node : nodes) {
			if(newNode.equals(node)){
				check = false;
				break;
			}
		}
		if(check){
			nodes.add(newNode);
			edges.put(newNode, new HashSet<Node>());
			
		}
    }

    public void addEdge(int p1, MethodNode m1, ClassNode c1,
			int p2, MethodNode m2, ClassNode c2) {
	// ...
		Node node1 = new Node(p1, m1, c1);
		Node node2 = new Node(p2, m2, c2);

		addNode(p1, m1, c1);
		addNode(p2, m2, c2);
		
		edges.get(node1).add(node2);
	
    }
	
	public void deleteNode(int p, MethodNode m, ClassNode c) {
		// ...
		Node delNode = new Node(p, m, c);
		boolean check = false;

		for(Node node : nodes) {
			if(delNode.equals(node)){
				check = true;
				break;
			}
		}
		
		if(check){
			edges.remove(delNode);
			for(Set<Node> subset : edges.values()){
				subset.remove(delNode);
			}
			nodes.remove(delNode);
		}

    }
	
    public void deleteEdge(int p1, MethodNode m1, ClassNode c1,
						int p2, MethodNode m2, ClassNode c2) {
		// ...
		Node node1 = new Node(p1, m1, c1);
		Node node2 = new Node(p2, m2, c2);
		boolean check = false;
		
		for(Node node : nodes) {
			if(node1.equals(node)){
				check = true;
				break;
			}
		}
		if(check){
			edges.get(node1).remove(node2);
		}
    }
	

    public boolean isReachable(int p1, MethodNode m1, ClassNode c1,
			       int p2, MethodNode m2, ClassNode c2) {
		// ...
		Node node1 = new Node(p1, m1, c1);
		Node node2 = new Node(p2, m2, c2);
		boolean check1 = false, check2 = false;
		
		for(Node node : nodes) {
			if(node1.equals(node)){
				check1 = true;
			}
			else if(node2.equals(node)){
				check2 = true;
			}
		}

		if(check1 && check2){
			Stack<Node> stack = new Stack<>();
			Map<Node,Boolean> visited = new HashMap<Node,Boolean>();
			for(Node node : nodes){
				visited.put(node, false);
			}
			stack.push(node1);
			while(!stack.empty()){
				Node temp = stack.peek(); 
				stack.pop();
				if(!visited.get(temp)){
					if(temp.equals(node2)){
						return true;
					}
					else{
						visited.put(temp, true);
						for(Node child : edges.get(temp)){
							if(!visited.get(child)){
								stack.push(child);
							}
						}
					}
				}
			}
			return false;
		}
		else return false;

    }
}
