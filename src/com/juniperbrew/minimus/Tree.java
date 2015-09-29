package com.juniperbrew.minimus;

import com.sun.javafx.scene.control.skin.VirtualFlow;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Juniperbrew on 28.9.2015.
 */
public class Tree<T> {
    private Node<T> root;

    public Tree(T rootData) {
        root = new Node<T>();
        root.data = rootData;
        root.children = new ArrayList<>();
    }

    public void addToRoot(T nodeData){
        Node<T> node = new Node<>();
        node.data = nodeData;
        node.children = new ArrayList<>();
        root.children.add(node);
    }

    public Node<T> getRoot(){
        return root;
    }

    public Node<T> addToNode(Node<T> parent, T nodeData){
        Node<T> node = new Node<>();
        node.data = nodeData;
        node.children = new ArrayList<>();
        parent.children.add(node);
        return node;
    }

    public static class Node<T> {
        private T data;
        private Node<T> parent;
        private List<Node<T>> children;

        public T getValue(){
            return data;
        }

        public List<Node<T>> getChildren(){
            return children;
        }

        public boolean hasChildren(){
            if(children!=null){
                return children.size()>0;
            }else{
                return false;
            }
        }
        public Node<T> getFirstChild(){
            return children.get(0);
        }
    }
}