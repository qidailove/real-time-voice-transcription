package com.winhong.yuebase.realtimevoicetranscription;

import java.util.List;

public class NodeTree {

    private String name;

    private String value;

    private int level;

    private List<NodeTree> childrenList;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public List<NodeTree> getChildrenList() {
        return childrenList;
    }

    public void setChildrenList(List<NodeTree> childrenList) {
        this.childrenList = childrenList;
    }

    @Override
    public String toString() {
        return "NodeTree{" +
                "name='" + name + '\'' +
                ", value='" + value + '\'' +
                ", level=" + level +
                ", childrenList=" + childrenList +
                '}';
    }
}
