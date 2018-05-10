////////////////////////////////////////////////////////////////////////////////
//
//      MP 4 - cs585
//
//      Paul Chase
//
//      This implements a cfg style grammar
////////////////////////////////////////////////////////////////////////////////

import com.sun.org.apache.xerces.internal.util.SynchronizedSymbolTable;

import java.io.*;
import java.text.*;
import java.util.*;
import java.util.regex.*;

//this class implements the third assignment for CS585
public class Grammar
{
	private Vector productions;
	private Vector nonTerminals;
	
	//this reads the grammar in from a file
	public Grammar(String f) throws Exception
	{
		productions = new Vector();
		productions.clear();
		nonTerminals = new Vector();
		nonTerminals.clear();
		//load the file
		BufferedReader br = new BufferedReader(new FileReader(f));
		Production p;
		String str = br.readLine();
		String rule[];
		while(str!=null)
		{
			rule = str.split("\t");
			p = new Production();
			p.probability = (new Float(rule[0])).floatValue();
			p.left = rule[1];
			p.right = rule[2].split(" ");
			p.dot = 0;
			p.start = 0;
			productions.add(p);
			addNonTerminal(rule[1]);
			str = br.readLine();
		}
	}

	//checks if we've seen this nonterminal; if we haven't, add it
	private void addNonTerminal(String s)
	{
		for(int i=0;i<nonTerminals.size();i++)
		{
			if(((String)nonTerminals.get(i)).compareTo(s)==0)
				return;
		}
		nonTerminals.add(s);
	}

	private final boolean isNonTerminal(String s)
	{
		//return true if it's a non-terminal
		for(int i=0;i<nonTerminals.size();i++)
		{
			if(((String)nonTerminals.get(i)).compareTo(s)==0)
				return true;
		}
		return false;
	}

	//this function predicts possible completions of p, and adds them to v
	private final void predict(Vector v, Production p,int pos)
	{
		Vector prods = getProds(p.right[p.dot]);
		Production q,r;
//		System.out.println("predict:"+p.toString());
		for(int j=0;j<prods.size();j++)
		{
			r = (Production)prods.get(j);
			q = new Production(r);
			q.dot = 0;
			q.start = pos;
			addProd(v,q);
		}
	}

	//this checks if we can scan s on the current production p
	private final boolean scan(Vector v, Production p, String s)
	{
		Production q;
//		System.out.println("scan:"+p.toString());
		if(p.right[p.dot].compareTo(s)==0)
		{
			//match - add it to the next vector
			q = new Production(p);
			q.dot = q.dot + 1;
			addProd(v,q);
			return true;
		}
		return false;
	}

	//this takes a completed production and tries to attach it back in the
	//cols table, putting any attachments into cur.
	private final void attach(Vector cols, Vector cur, Production p)
	{
		//if the next thing in one rule is the first thing in this rule,
		//we attach.  otherwise ignore
		Vector col;
		Production q,r, child;
		String s = p.left;
		boolean match = false;

		col = (Vector)cols.get(p.start);
		System.out.println("attach:"+p.toString());
		for(int j=0;j<col.size();j++)
		{
			q = (Production)col.get(j);
			if(q.right.length > q.dot)
				if(q.right[q.dot].compareTo(s)==0)
				{	//Attach!
					r = new Production(q);
					r.dot = r.dot + 1;
					if(r.children == null){
						r.spawn(q.right.length);
					}
					// check the probability of the current child and the new potential one.
					child = r.children[r.dot-1];
					System.out.println("Probabilty compare : " + child.recursiveLogComputation() +
							"  -----  " + p.recursiveLogComputation());
					// if the probability of the new child is bigger than the current one, change the child attached
					// to the current node.
					if (child.recursiveLogComputation() <= p.recursiveLogComputation()){
						r.spawn(r.dot-1, p);
						addProd(cur,r);
					}
				}
		}
	}

	//this parses the sentence
    //this parses the sentence
    public final Production parse(String sent[])
    {
        //this is a vector of vectors, storing the columns of the table
        Vector cols = new Vector();	cols.clear();
        //this is the current column; a vector of production indices
        Vector cur = new Vector();	cur.clear();
        //this is the next column; a vector of production indices
        Vector next = new Vector();	next.clear();
        //add the first symbol
        cur.add((Production)getProds("ROOT").get(0));
        Production p;
        for(int pos=0;pos<=sent.length;pos++)
        {
            System.out.println("Pos = " + pos);
            int i=0;
            boolean match = false;
            //check through the whole vector, even as it gets bigger
            while(i!=cur.size())
            {
                p = (Production)cur.get(i);
                if(p.right.length > p.dot)
                {	//predict and scan
                    if(sent.length == pos)
                    {
                        match = true;
                    } else{
                        if(isNonTerminal(p.right[p.dot]))
                        {
                            //predict adds productions to cur
                            predict(cur,p,pos);
                        }else{
                            //scan adds productions to next
                            System.out.println("scan: " + p.toString() + " ("+pos+")= " + sent[pos]);
                            if(scan(next,p,sent[pos])) {
                                System.out.println("  Found: " + sent[pos]);
                                match = true;
                            }
                        }
                    }
                } else {	//attach
                    attach(cols,cur,p);
                    if(sent.length == pos)
                    {
                        match = true;
                    }
                }
                i++;
                //when using a gargantuan grammar
                //this spits out stuff if it's taking a long time.
                if(i%100 == 0)
                    System.out.print(".");
            }
            System.out.println("Match = " + match);
            cols.add(cur);
            if(!match)
            {
                printTable(cols,sent);
                System.out.println("Failed on: "+ cur);
                return null;
            }
            cur = next;
            next = new Vector();	next.clear();
            System.out.println();
        }

        //print the Earley table
        //Comment this out once you've got parses printing; it's
        //only here for your evaluation.
        printTable(cols,sent);

        //Right now we simply check to see if a parse exists;
        //in other words, we see if there's a "ROOT -> x x x ."
        //production in the last column.  If there is, it's returned; otherwise
        //return null.
        Production finished = new Production((Production)getProds("ROOT").get(0));
        finished.dot = finished.right.length;

        Production node;
        Vector rules = new Vector();
		rules.clear();
        cur = (Vector) cols.lastElement();
        node = (Production) cur.lastElement();
        // Check if the final parsing end with a root, if not, return NULL
		boolean hasRoot = false;
		for(int i=0; i<cur.size();i++){
			node = (Production)cur.get(i);
			if(node.equals(finished)){
				hasRoot = true;
			}
		}
		if(hasRoot){

			// Get all the rules ending with a . in order to parse the tree
			rules = getFinalProds(cols);
			Production root = new Production();
			for(int i = 0; i<rules.size(); i++){
				root = (Production) rules.get(i);
				// if the node has the S in the left, it is the root of the tree, we can return it.
				if(root.left.equals("s")){
					return root;
				}
			}
			return null;
		} else {
			return null;
		}

    }

	/**this prints the table in a human-readable fashion.
	 * format is one column at a time, lists the word in the sentence
	 * and then the productions for that column.
	 * @param cols The columns of the table
	 * @param sent the sentence
	 */
	private final void printTable(Vector cols,String sent[])
	{
		Vector col;
		//print one column at a time
		for(int i=0;i<cols.size();i++)
		{
			col = (Vector)cols.get(i);
			//sort the columns by 
			if(i>0)
			{
				System.out.println("\nColumn "+i+": "+sent[i-1]+"\n------------------------");
			}else{
				System.out.println("\nColumn "+i+": ROOT\n------------------------");
			}
			
			for(int j=0;j<col.size();j++)
			{
				System.out.println(((Production)col.get(j)).toString());
			}
		}
	}

	//this adds a production p to the vector v of production indices
	//it also checks for duplicate indices, and skips those
	private final void addProd(Vector v, Production p)
	{
		//check for duplicates
		for(int i=0;i<v.size();i++)
			if(((Production)v.get(i)).equals(p))
				return;
		v.add(p);
	}

	//This runs through the columns and returns all the fully parsed productions
	//i.e. those with little dots at the very end.
	private final Vector getFinalProds(Vector cols)
	{
		Vector cur;
		Vector prods = new Vector();	prods.clear();
		Production p;
		for(int i=0; i<cols.size(); i++)
		{
			cur = (Vector)cols.get(i);
			for(int j=0;j<cur.size();j++)
			{
				p = (Production)cur.get(j);
				if(p.right.length == p.dot)
				{
					if(p.left.compareTo("ROOT")!=0)
					{
						prods.add(p);
					}
				}
			}
		}
		//convert it to an array for returning
		return prods;
	}

	//this returns true if a string is in the grammar, false otherwise
	//it's not exactly "comprehensive"... mostly it'll just see if all
	//the tokens in the sentence are terminals.
	private final boolean inGrammar(String s)
	{
		boolean found=false;
		Production p;
		for(int i=0;i<productions.size();i++)
		{
			p = (Production)productions.get(i);
			for(int j=0;j<p.right.length;j++)
				if(p.right[j].indexOf(s)!=-1)
					found = true;
			//we can't have a string equal to a non-terminal
			if(p.left.compareTo(s)==0)
			{
				System.out.println("String contains a non-terminal - cannot parse");
				return false;
			}
		}
		return found;
	}

	//this returns a vector of productions with a left side matching the
	//argument; happy string comparing.
	private final Vector getProds(String left)
	{
		//we store it in a vector for safekeeping
		Vector prods = new Vector();	prods.clear();
		Production p;
		for(int i=0;i<productions.size();i++)
		{
			p = (Production)productions.get(i);
			if(p.left.compareTo(left)==0)
				prods.add(p);
		}
		//convert it to an array for returning
		return prods;
	}

	//this checks if the given string[] has a parse tree with this grammar
	//
	public final boolean canParse(String sent[])
	{
		//check if all symbols are in the grammar
		for(int i=0;i<sent.length;i++)
			if(!inGrammar(sent[i]))
				return false;
		return true;
	}

	//this prints out the grammar
	public void print()
	{
		System.out.println(this.toString());
	}

	//what does every toString function do?
	public String toString()
	{
		String ret = "";
		for(int i=0;i<productions.size();i++)
			ret = ret + ((Production)productions.get(i)).toString() + "\n";
		return ret;
	}
}
