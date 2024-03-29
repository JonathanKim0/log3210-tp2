package analyzer.visitors;

import analyzer.SemantiqueError;
import analyzer.ast.*;
import com.sun.org.apache.xpath.internal.operations.Bool;
import jdk.nashorn.internal.codegen.types.Type;

import java.io.Console;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created: 19-01-10
 * Last Changed: 23-02-03
 * Author: Félix Brunet
 * <p>
 * Description: Ce visiteur explorer l'AST est renvois des erreurs lorsqu'une erreur sémantique est détectée.
 */

public class SemantiqueVisitor implements ParserVisitor {

    private final PrintWriter m_writer;

    private HashMap<String, VarType> SymbolTable = new HashMap<>(); // mapping variable -> type

    // variable pour les metrics
    public int VAR = 0;
    public int WHILE = 0;
    public int IF = 0;
    public int ENUM_VALUES = 0;
    public int OP = 0;

    public SemantiqueVisitor(PrintWriter writer) {
        m_writer = writer;
    }

    /*
    IMPORTANT:
    *
    * L'implémentation des visiteurs se base sur la grammaire fournie (Grammaire.jjt). Il faut donc la consulter pour
    * déterminer les noeuds enfants à visiter. Cela vous sera utile pour lancer les erreurs au bon moment.
    * Pour chaque noeud, on peut :
    *   1. Déterminer le nombre d'enfants d'un noeud : jjtGetNumChildren()
    *   2. Visiter tous les noeuds enfants: childrenAccept()
    *   3. Accéder à un noeud enfant : jjtGetChild()
    *   4. Visiter un noeud enfant : jjtAccept()
    *   5. Accéder à m_value (type) ou m_ops (vecteur des opérateurs) selon la classe de noeud AST (src/analyser/ast)
    *
    * Cela permet d'analyser l'intégralité de l'arbre de syntaxe abstraite (AST) et d'effectuer une analyse sémantique du code.
    *
    * Le Visiteur doit lancer des erreurs lorsqu'une situation arrive.
    *
    * Pour vous aider, voici le code à utiliser pour lancer les erreurs :
    *
    * - Utilisation d'identifiant non défini :
    *   throw new SemantiqueError("Invalid use of undefined Identifier " + node.getValue());
    *
    * - Plusieurs déclarations pour un identifiant. Ex : num a = 1; bool a = true; :
    *   throw new SemantiqueError(String.format("Identifier %s has multiple declarations", varName));
    *
    * - Utilisation d'un type num dans la condition d'un if ou d'un while :
    *   throw new SemantiqueError("Invalid type in condition");
    *
    * - Utilisation de types non valides pour des opérations de comparaison :
    *   throw new SemantiqueError("Invalid type in expression");
    *
    * - Assignation d'une valeur à une variable qui a déjà reçu une valeur d'un autre type. Ex : a = 1; a = true; :
    *   throw new SemantiqueError(String.format("Invalid type in assignation of Identifier %s", varName));
    *
    * - Le type de retour ne correspond pas au type de fonction :
    *   throw new SemantiqueError("Return type does not match function type");
    * */


    @Override
    public Object visit(SimpleNode node, Object data) {
        return data;
    }

    @Override
    public Object visit(ASTProgram node, Object data) {
        node.childrenAccept(this, data);
        m_writer.print(String.format("{VAR:%d, WHILE:%d, IF:%d, ENUM_VALUES:%d, OP:%d}", this.VAR, this.WHILE, this.IF, this.ENUM_VALUES, this.OP));
        return null;
    }

    // Enregistre les variables avec leur type dans la table symbolique.
    @Override
    public Object visit(ASTDeclaration node, Object data) {
        //TODO
        this.VAR++;
        int numChildren = node.jjtGetNumChildren();
        String varName = ((ASTIdentifier) node.jjtGetChild(numChildren - 1)).getValue();
        if (SymbolTable.containsKey(varName)) {
            throw new SemantiqueError(String.format("Identifier %s has multiple declarations.", varName));
        }
        String type = numChildren == 1 ? node.getValue() : ((ASTIdentifier) node.jjtGetChild(0)).getValue();
        if(type != null) {
            if(type.equals("num")) {
                SymbolTable.put(varName, VarType.Number);
            } else if(type.equals("bool")) {
                SymbolTable.put(varName, VarType.Bool);
            } else if (!SymbolTable.containsKey(type) || SymbolTable.get(type).equals(VarType.EnumValue)) {
                throw new SemantiqueError(String.format("Identifier %s has been declared with the type %s that does not exist", varName, type));
            } else {
                SymbolTable.put(varName, VarType.EnumVar);
                System.out.println(SymbolTable);
            }
        }
        return null;
    }

    @Override
    public Object visit(ASTBlock node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTStmt node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    // Méthode qui pourrait être utile pour vérifier le type d'expression dans une condition.
    private DataStruct callChildrenCond(Node node, int i) {
        // TODO
        DataStruct d = new DataStruct();
        node.jjtGetChild(i).jjtAccept(this, d);
        return d;
    }

    // les structures conditionnelle doivent vérifier que leur expression de condition est de type booléenne
    // On doit aussi compter les conditions dans les variables IF et WHILE
    @Override
    public Object visit(ASTIfStmt node, Object data) {
        // TODO
        this.IF++;

        DataStruct d = callChildrenCond(node, 0);

        if (d.type != VarType.Bool) {
            throw new SemantiqueError("Invalid type in condition");
        }

        int numChildren = node.jjtGetNumChildren();
        for (int i = 1; i < numChildren; i++) {
            node.jjtGetChild(i).jjtAccept(this, data);
        }
        return null;
    }

    @Override
    public Object visit(ASTWhileStmt node, Object data) {
        // TODO
        this.WHILE++;
        DataStruct d = callChildrenCond(node, 0);
        if (d.type != VarType.Bool) {
            throw new SemantiqueError("Invalid type in condition");
        }

        int numChildren = node.jjtGetNumChildren();
        for (int i = 1; i < numChildren; i++) {
            node.jjtGetChild(i).jjtAccept(this, data);
        }
        return null;
    }

    // On doit vérifier que le type de la variable est compatible avec celui de l'expression.
    @Override
    public Object visit(ASTAssignStmt node, Object data) {
        System.out.println("ASTAssignStmt");

        String varName = ((ASTIdentifier) node.jjtGetChild(0)).getValue();
        DataStruct d = new DataStruct();

        if (node.jjtGetNumChildren() > 1) {
            node.jjtGetChild(1).jjtAccept(this, d);
            System.out.println("this node: "+node.jjtGetChild(1));
            System.out.println("this type: "+d.type);
            if (d.type != SymbolTable.get(varName)) {
                if (!(d.type.equals(VarType.EnumValue) && SymbolTable.get(varName).equals(VarType.EnumVar) && SymbolTable.containsKey(varName))) {
                    throw new SemantiqueError(String.format("Invalid type in assignation of Identifier %s", varName));
                }
            }
        }
        return null;
    }

    @Override
    public Object visit(ASTEnumStmt node, Object data) {
        // TODO
        String varName = ((ASTIdentifier) node.jjtGetChild(0)).getValue();
        if (SymbolTable.containsKey(varName)) {
            throw new SemantiqueError(String.format("Identifier %s has multiple declarations.", varName));
        }
        SymbolTable.put(varName, VarType.EnumType);
        int numChildren = node.jjtGetNumChildren();
        for(int i = 1; i < numChildren; i++) {
            this.ENUM_VALUES++;
            varName = ((ASTIdentifier) node.jjtGetChild(i)).getValue();
            if (SymbolTable.containsKey(varName)) {
                throw new SemantiqueError(String.format("Identifier %s has multiple declarations.", varName));
            }
            SymbolTable.put(varName, VarType.EnumValue);
        }
        return null;
    }

    @Override
    public Object visit(ASTSwitchStmt node, Object data) {
        // TODO
        String varName = ((ASTIdentifier) node.jjtGetChild(0)).getValue();
        if(SymbolTable.get(varName)!=VarType.EnumVar && SymbolTable.get(varName)!=VarType.Number && SymbolTable.get(varName)!=VarType.Bool){ //check if var,number,bool
            throw new SemantiqueError(String.format("Invalid type in switch of Identifier %s", varName));
        }

        int numChildren = node.jjtGetNumChildren();
        DataStruct pastData = callChildrenCond(node.jjtGetChild(1), 0);
        Node pastNode = node.jjtGetChild(1);
        HashMap<VarType, String> printTypes = new HashMap() {{
            put(null, "Identifier");
            put(VarType.Number, "integer");
            put(VarType.Bool, "boolean");
        }};

        for(int i = 2; i < numChildren; i++) {
            DataStruct d = callChildrenCond(node.jjtGetChild(i), 0);
            VarType pastType = pastData.type != null ? pastData.type : SymbolTable.get(((ASTIdentifier) pastNode.jjtGetChild(0)).getValue());
            VarType type = d.type != null ? d.type : SymbolTable.get(((ASTIdentifier) node.jjtGetChild(i).jjtGetChild(0)).getValue());
            if(pastType != type){
                if(type == VarType.Number) {
                    throw new SemantiqueError(String.format("Invalid type in case of %s %s", printTypes.get(d.type), ((ASTIntValue)node.jjtGetChild(i).jjtGetChild(0)).getValue()));
                } else if (type == VarType.Bool) {
                    throw new SemantiqueError(String.format("Invalid type in case of %s %s", printTypes.get(d.type), ((ASTBoolValue)node.jjtGetChild(i).jjtGetChild(0)).getValue()));
                } else if (pastNode.jjtGetChild(0) instanceof ASTIdentifier) {
                    throw new SemantiqueError(String.format("Invalid type in case of %s %s", printTypes.get(d.type), ((ASTIdentifier)pastNode.jjtGetChild(0)).getValue()));
                }
            }
            pastData = callChildrenCond(node.jjtGetChild(1), 0);
        }
        if(SymbolTable.get(varName) != (pastData.type==null?VarType.EnumVar:pastData.type)){
            throw new SemantiqueError(String.format("Invalid type in switch of Identifier %s", varName));
        }

        for (int i = 1; i < numChildren; i++) {
            ASTCaseStmt n = (ASTCaseStmt) node.jjtGetChild(i);
            n.jjtAccept(this, data);
        }
        return null;
    }

    @Override
    public Object visit(ASTCaseStmt node, Object data) {
        // TODO
        System.out.println("ASTCaseStmt");

        String parent = ((ASTIdentifier) node.jjtGetParent().jjtGetChild(0)).getValue();

        if(node.jjtGetChild(0) instanceof ASTIdentifier) {
            String var = ((ASTIdentifier) node.jjtGetChild(0)).getValue();
            if(SymbolTable.get(parent) == VarType.EnumVar) {
                if(SymbolTable.get(var) != VarType.EnumValue) {
                    throw new SemantiqueError(String.format("Invalid type in case of Identifier %s", var));
                }
            } else if(SymbolTable.get(var) != SymbolTable.get(parent)) {
                throw new SemantiqueError(String.format("Invalid type in case of Identifier %s", var));
            }
        }
        node.jjtGetChild(1).jjtAccept(this, data);

        return null;
    }

    @Override
    public Object visit(ASTExpr node, Object data) {
        // TODO
        System.out.println("ASTExpr");
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTCompExpr node, Object data) {
        /*
            Attention, ce noeud est plus complexe que les autres :
            - S’il n'a qu'un seul enfant, le noeud a pour type le type de son enfant.
            - S’il a plus d'un enfant, alors il s'agit d'une comparaison. Il a donc pour type "Bool".
            - Il n'est pas acceptable de faire des comparaisons de booléen avec les opérateurs < > <= >=.
            - Les opérateurs == et != peuvent être utilisé pour les nombres et les booléens, mais il faut que le type
            soit le même des deux côtés de l'égalité/l'inégalité.
        */
        // TODO
        System.out.println("ASTCompExpr");
        int numChildren = node.jjtGetNumChildren();
        VarType[] equalityTypes = new VarType[2];

        if (numChildren > 1) {
            this.OP++;
        }
        for (int i = 0; i < numChildren; i++) {
            DataStruct d = callChildrenCond(node, i);
            if (numChildren == 1) {
                ((DataStruct) data).type = d.type;
            } else {
                String operator = node.getValue();
                if (operator.equals(">") || operator.equals("<") || operator.equals(">=") || operator.equals("<=")) {
                    if (d.type == VarType.Bool) {
                        throw new SemantiqueError("Invalid type in expression");
                    }
                } else if (operator.equals("==") || operator.equals("!=")) {
                    equalityTypes[i] = d.type;
                    if (i == 1 && equalityTypes[0] != equalityTypes[1]) {
                        throw new SemantiqueError("Invalid type in expression");
                    }
                } else {
                    throw new SemantiqueError("Shouldn't fall here");
                }
                ((DataStruct) data).type = VarType.Bool;
            }
        }
        return null;
    }

    /*
        Opérateur binaire :
        - s’il n'y a qu'un enfant, aucune vérification à faire.
        - Par exemple, un AddExpr peut retourner le type "Bool" à condition de n'avoir qu'un seul enfant.
     */
    @Override
    public Object visit(ASTAddExpr node, Object data) {
        // TODO
        System.out.println("ASTAddExpr");
        int numChildren = node.jjtGetNumChildren();
        if (numChildren > 1) {
            this.OP++;
        }
        for (int i = 0; i < numChildren; i++) {
            DataStruct d = callChildrenCond(node, i);

            if(numChildren > 1 && d.type != VarType.Number) {
                throw new SemantiqueError("Invalid type in expression");
            }

            if (d.type != null) {
                ((DataStruct) data).type = d.type;
            }
        }
        return null;
    }

    @Override
    public Object visit(ASTMulExpr node, Object data) {
        // TODO
        System.out.println("ASTMulExpr");
        int numChildren = node.jjtGetNumChildren();
        if (numChildren > 1) {
            this.OP++;
        }
        for (int i = 0; i < numChildren; i++) {
            DataStruct d = callChildrenCond(node, i);
            if (numChildren > 1 && d.type != VarType.Number) {
                throw new SemantiqueError("Invalid type in expression");
            }

            if (d.type != null) {
                ((DataStruct) data).type = d.type;
            }
        }
        return null;
    }

    @Override
    public Object visit(ASTBoolExpr node, Object data) {
        // TODO
        System.out.println("ASTBoolExpr");

        int numChildren = node.jjtGetNumChildren();
        if (numChildren > 1) {
            this.OP++;
        }
        for (int i = 0; i < numChildren; i++) {
            DataStruct d = callChildrenCond(node, i);
            if (numChildren == 1) {
                ((DataStruct)data).type = d.type;
            }
            else {
                if (d.type != VarType.Bool) {
                    throw new SemantiqueError("Invalid type in expression");
                }
                ((DataStruct)data).type = VarType.Bool;
            }
        }
        return null;
    }

    /*
        Opérateur unaire
        Les opérateurs unaires ont toujours un seul enfant. Cependant, ASTNotExpr et ASTUnaExpr ont la fonction
        "getOps()" qui retourne un vecteur contenant l'image (représentation str) de chaque token associé au noeud.
        Il est utile de vérifier la longueur de ce vecteur pour savoir si un opérande est présent.
        - S’il n'y a pas d'opérande, ne rien faire.
        - S’il y a un (ou plus) opérande, il faut vérifier le type.
    */
    @Override
    public Object visit(ASTNotExpr node, Object data) {
        // TODO
        System.out.println("ASTNotExpr");
        node.jjtGetChild(0).jjtAccept(this, data);
        if (node.getOps().size() > 0) {
            this.OP++;
            if (((DataStruct) data).type != VarType.Bool) {
                throw new SemantiqueError("Invalid type in expression");
            }
        }
        return null;
    }

    @Override
    public Object visit(ASTUnaExpr node, Object data) {
        // TODO
        System.out.println("ASTUnaExpr");
        node.jjtGetChild(0).jjtAccept(this, data);
        if (node.getOps().size() > 0) {
            this.OP++;
            if (((DataStruct) data).type != VarType.Number) {
                throw new SemantiqueError("Invalid type in expression");
            }
        }
        return null;
    }

    /*
        Les noeud ASTIdentifier ayant comme parent "GenValue" doivent vérifier leur type.
        On peut envoyer une information à un enfant avec le 2e paramètre de jjtAccept ou childrenAccept.
     */
    @Override
    public Object visit(ASTGenValue node, Object data) {
        System.out.println("ASTGenValue");
        node.childrenAccept(this, data);
        return null;
    }


    @Override
    public Object visit(ASTBoolValue node, Object data) {
        System.out.println("ASTBoolValue");
        ((DataStruct) data).type = VarType.Bool;
        return null;
    }

    @Override
    public Object visit(ASTIdentifier node, Object data) {
        System.out.println("ASTIdentifier");
        if (node.jjtGetParent() instanceof ASTGenValue) {
            String varName = node.getValue();
            VarType varType = SymbolTable.get(varName);

            ((DataStruct) data).type = varType;
        }

        return null;
    }

    @Override
    public Object visit(ASTIntValue node, Object data) {
        System.out.println("ASTIntValue");
        ((DataStruct) data).type = VarType.Number;
        return null;
    }


    //des outils pour vous simplifier la vie et vous enligner dans le travail
    public enum VarType {
        Bool,
        Number,
        EnumType,
        EnumVar,
        EnumValue
    }


    private class DataStruct {
        public VarType type;

        public DataStruct() {
        }

        public DataStruct(VarType p_type) {
            type = p_type;
        }

    }
}
