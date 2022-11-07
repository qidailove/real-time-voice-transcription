package com.winhong.yuebase.realtimevoicetranscription;

import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.xml.soap.Node;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class test {
    public static void main(String[] args) throws IOException {
        FileInputStream fileInputStream = new FileInputStream("C:\\Users\\anyuexi\\Desktop\\a.xlsx");
        XSSFWorkbook xssfWorkbook = new XSSFWorkbook(fileInputStream);
        XSSFSheet sheet = xssfWorkbook.getSheetAt(0);
        XSSFRow row = sheet.getRow(0);

        NodeTree nodeTree = new NodeTree();
        nodeTree.setName(row.getCell(0).toString());
        nodeTree.setLevel(1);
        nodeTree.setChildrenList(new ArrayList<>());
        for (int i = 1; i < row.getLastCellNum(); i ++){
            String[] split = row.getCell(i).toString().split("/");
            LinkedList<String> list = new LinkedList();
            HashMap map = new HashMap();
            for (int z = 0; z < split.length; z++){
                map.put(z + 1,split[z]);
            }
            Set set = map.keySet();

            for (int j = 1; j < set.size(); j++){
                setTree((String) map.get(j),j, (String) map.get(j+1),j + 1,nodeTree);
            }
        }
        System.out.println(nodeTree);
    }


    public static void setTree(String name0, int level0, String name1, int level1, NodeTree rootTree){
        if (level0 == rootTree.getLevel()){
            if (name0.equals(rootTree.getName())){
                List<NodeTree> childrenList = rootTree.getChildrenList();
                NodeTree nodeTree = new NodeTree();
                nodeTree.setLevel(level1);
                nodeTree.setName(name1);
                nodeTree.setChildrenList(new ArrayList<>());
                childrenList.add(nodeTree);
            }
        }else {
            List<NodeTree> childrenList = rootTree.getChildrenList();
            for (int j = 0; j < childrenList.size(); j++){
                setTree(name0,level0,name1,level1,childrenList.get(j));
            }
        }
    }

}
