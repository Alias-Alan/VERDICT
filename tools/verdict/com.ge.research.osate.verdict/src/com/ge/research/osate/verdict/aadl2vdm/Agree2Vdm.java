package com.ge.research.osate.verdict.aadl2vdm;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.osate.aadl2.AnnexSubclause;
import org.osate.aadl2.NamedElement;
import org.osate.aadl2.Port;
import org.osate.aadl2.Property;
import org.osate.aadl2.SystemType;

import verdict.vdm.vdm_lustre.BinaryOperation;
import verdict.vdm.vdm_lustre.ContractItem;
import verdict.vdm.vdm_lustre.Expression;
import verdict.vdm.vdm_lustre.IfThenElse;
import verdict.vdm.vdm_lustre.SymbolDefinition;
import verdict.vdm.vdm_model.Model;
import com.rockwellcollins.atc.agree.agree.Arg;
import com.rockwellcollins.atc.agree.agree.BinaryExpr;
import com.rockwellcollins.atc.agree.agree.BoolLitExpr;
import com.rockwellcollins.atc.agree.agree.CallExpr;
import com.rockwellcollins.atc.agree.agree.Contract;
import com.rockwellcollins.atc.agree.agree.DoubleDotRef;
import com.rockwellcollins.atc.agree.agree.EnumLitExpr;
import com.rockwellcollins.atc.agree.agree.AgreeContractSubclause;
import com.rockwellcollins.atc.agree.agree.AgreeContract;
import com.rockwellcollins.atc.agree.agree.EqStatement;
import com.rockwellcollins.atc.agree.agree.Expr;
import com.rockwellcollins.atc.agree.agree.GuaranteeStatement;
import com.rockwellcollins.atc.agree.agree.IfThenElseExpr;
import com.rockwellcollins.atc.agree.agree.IntLitExpr;
import com.rockwellcollins.atc.agree.agree.NamedElmExpr;
import com.rockwellcollins.atc.agree.agree.PreExpr;
import com.rockwellcollins.atc.agree.agree.PrimType;
import com.rockwellcollins.atc.agree.agree.RecordLitExpr;
import com.rockwellcollins.atc.agree.agree.SelectionExpr;
import com.rockwellcollins.atc.agree.agree.SpecStatement;
import com.rockwellcollins.atc.agree.agree.Type;
import com.rockwellcollins.atc.agree.agree.UnaryExpr;
public class Agree2Vdm {
	/**
	 * The execute() method performs a set of tasks for translating AADL to VDM
	 *
	 * @param inputDir a reference to a directory
	 *
	 * */
	public Model execute(File inputDir)
   {
		System.err.println("Successfully entered Agree2Vdm Translator! \n\n\n");
	    Model m = new Model();
	    Aadl2Vdm aadl2vdm= new Aadl2Vdm();
		m = populateVDMFromAadlObjects(aadl2vdm.preprocessAadlFiles(inputDir), m);
		//System.err.println("Working Directory = " + System.getProperty("user.dir"));
		//File testXml = new File("/Users/212810885/Desktop/testXML.xml");
		//System.err.println("Created File object to store Xml");
		//VdmTranslator.marshalToXml(m, testXml);
		//System.err.println("Marshalled Model to XML");
		return m;
   }
	/**
	 * Assume the input is correct without any syntax errors
	 * Populate mission req, cyber and safety reqs and rels from AADL objects
	 *
	 *  @param objects a List of AADL objects,
	 * 	@param model an empty VDM model to populate
	 *  @return a populated VDM model
	 *
	 * */
	public Model populateVDMFromAadlObjects(List<EObject> objects, Model model) {
		// variables for extracting data from the AADL object
		List<SystemType> systemTypes = new ArrayList<>();
		// extracting data from the AADLObject
		for(EObject obj : objects) {
			if (obj instanceof SystemType) {
				//obtaining system Types
				systemTypes.add((SystemType) obj);
			}
		} // end of extracting data from the AADLObjec
		/* Translating agree annex in System Types */
		model = translateAgreeAnnex(systemTypes, model);
		//return the final model
		return model;
	}
	
	private Model translateAgreeAnnex(List<SystemType> systemTypes, Model model) {
		for(SystemType sysType : systemTypes) {
			System.out.println("Processing systemType "+sysType.getFullName());
			verdict.vdm.vdm_model.ComponentType packComponent = new verdict.vdm.vdm_model.ComponentType();
			// unpacking sysType
			for(AnnexSubclause annex : sysType.getOwnedAnnexSubclauses()) {
				if(annex.getName().equalsIgnoreCase("agree")) {
					//populating agree contracts in the vdm component type -- SHOULD ADD THIS CODE TO AADL2VDM
					verdict.vdm.vdm_lustre.ContractSpec contractSpec = new verdict.vdm.vdm_lustre.ContractSpec();
					System.out.println("Processing Agree Annex of the system ");
					EList<EObject> annexContents= annex.eContents();
					if(annexContents.isEmpty()) {
						System.out.println("Empty Agree Annex.");
					}
					for(EObject clause : annexContents) {
						//mapping to AgreeContractSubclause
						AgreeContractSubclause agreeContractSubClause = (AgreeContractSubclause)clause;
						//extracting contract
						Contract contract = agreeContractSubClause.getContract();
						if(contract instanceof AgreeContract) {
							//getting specStatements
							EList<SpecStatement> specStatements = ((AgreeContract) contract).getSpecs();
							for(SpecStatement specStatement : specStatements) {
								if (specStatement instanceof EqStatement) {
									System.out.println("########Found type EqStatement#################");
									EqStatement eqStmt = (EqStatement)specStatement;
									//translate EqStatement in Agree to SymbolDefinition in vdm
									SymbolDefinition symbDef = translateEqStatement(eqStmt, model);
									//Add agree variable/symbol definition to the contractSpec in vdm
									contractSpec.getSymbol().add(symbDef);
									System.out.println("########End of EqStatement processing##########");
								} else if (specStatement instanceof GuaranteeStatement) {
									System.out.println("########Found type GuaranteeStatement##########");
									GuaranteeStatement guaranteeStmt = (GuaranteeStatement)specStatement;
									ContractItem contractItem = translateGuaranteeStatement(guaranteeStmt);
									contractSpec.getGuarantee().add(contractItem);
									System.out.println("########End of GuaranteeStatement processing####");
								} else {
									System.out.println("Element not recognizable"+clause.eContents().toString());
								}
							}
						} else {
							System.out.println("Not of type AgreeContract");
						}
					}
					//populating agree contract details in the componentType instance in vdm
					packComponent.setContract(contractSpec);
				}
			}// End of unpacking sysType
			//adding to the list of componentTypes of the Model object
			model.getComponentType().add(packComponent);
		}
		return model;
	}
	//method to map agree statements of the type EqStatement 
	//that have the form: 'eq' Arg (',' Arg)* '=' Expr ';'
	//and create corresponding "SymbolDefinition" for the "ContractSpec" in the vdm model 
	private SymbolDefinition translateEqStatement(EqStatement eqStmt, Model model) {
		SymbolDefinition symbDef = new verdict.vdm.vdm_lustre.SymbolDefinition();		
		//get the right side/expression
		Expr agreeExpr = eqStmt.getExpr();
		Expression vdmExpression = getVdmExpressionFromAgreeExpression(agreeExpr);		
		//get left side of the equation
		EList<Arg> lhsArgs = eqStmt.getLhs();		
		for(Arg lhsArg : lhsArgs) {//left side has the variable names along with their types
			//set the id				
			symbDef.setName(lhsArg.getName());
			System.out.println("Variable name:"+symbDef.getName());
			//need to parse the type of the variable and should map to appropriate DataType value (plainType, subrangeType, arrayType, tupleType, enumType, recordType, userDefinedType) of the symbol
			Type type = lhsArg.getType();
			verdict.vdm.vdm_data.DataType dtype = new verdict.vdm.vdm_data.DataType();
			if(type instanceof PrimType) {
				PrimType primType = (PrimType)type;
				verdict.vdm.vdm_data.PlainType plaintype = verdict.vdm.vdm_data.PlainType.fromValue(primType.getName());
				dtype.setPlainType(plaintype);
			} else if(type instanceof DoubleDotRef) {
				DoubleDotRef ddrefType = (DoubleDotRef)type;
				System.out.println("DoubleDotRef Type get elm: "+ddrefType.getElm());//also of type PropertyImpl
				//verdict.vdm.vdm_data.RecordField  recField= new verdict.vdm.vdm_data.RecordField();
				//verdict.vdm.vdm_data.RecordType rtype = new verdict.vdm.vdm_data.RecordType();
				//recField.setName();
				//recField.setType();
				//rtype.getRecordField().add(recField);
				//dtype.setRecordType(rtype);
			} else {
				System.out.println("Type value is "+type.toString());
			}
			//set type
			symbDef.setDataType(dtype);			
			//set the expression as the value/definition for each variable on the left
			symbDef.setDefinition(vdmExpression);
		}
		return symbDef;
	}
	private ContractItem translateGuaranteeStatement(GuaranteeStatement guaranteeStmt) {
		ContractItem contractItem = new ContractItem();
		contractItem.setName(guaranteeStmt.getStr());
		System.out.println("Guarantee name: "+contractItem.getName());
		Expr agreeExpr = guaranteeStmt.getExpr();
		Expression vdmlustrExpr = getVdmExpressionFromAgreeExpression(agreeExpr);
		contractItem.setExpression(vdmlustrExpr);
		return contractItem;
	}
	//method to translate expression in Agree to expression in vdm
	private Expression getVdmExpressionFromAgreeExpression(Expr agreeExpr) {
		Expression vdmExpr = new Expression();		
		System.out.println("********************");
		if(agreeExpr instanceof IfThenElseExpr) {			
			IfThenElseExpr ifexpr = (IfThenElseExpr)agreeExpr;
			System.out.println("If then else expr");
			IfThenElse ifThenElseVal = new IfThenElse();//for vdm model	
			System.out.println("Processing ondition branch");
			Expression condExpr = getVdmExpressionFromAgreeExpression(ifexpr.getA());
			ifThenElseVal.setCondition(condExpr);
			System.out.println("Processing then branch");
			Expression thenBranchExpr = getVdmExpressionFromAgreeExpression(ifexpr.getB());
			ifThenElseVal.setThenBranch(thenBranchExpr);
			System.out.println("Processing else branch");
			Expression elseBranchExpr = getVdmExpressionFromAgreeExpression(ifexpr.getC());
			ifThenElseVal.setElseBranch(elseBranchExpr);		
			vdmExpr.setConditionalExpression(ifThenElseVal);
		} else if(agreeExpr instanceof CallExpr) {
			System.out.println("Call expr");
			CallExpr callExpr = (CallExpr)agreeExpr;
			DoubleDotRef ddref = (DoubleDotRef)callExpr.getRef();
			System.out.println("CallExprDoubleDotRef "+ddref.getElm());
			EList<Expr> callExprArgs = callExpr.getArgs();
			//below are the parameters passed to the function call
			for(Expr callExprArg: callExprArgs) {
				System.out.println("callExpr arg "+callExprArg);
				Expression argExpr = getVdmExpressionFromAgreeExpression(callExprArg);
			}
			//TODO: should set vdmExpr once the above function call with arguments are processed right
			//If if it is AgreeNodes:: then Can do vdmExpr.setCall()
		} else if(agreeExpr instanceof NamedElmExpr) {
			System.out.println("Named Elm expr");
			NamedElmExpr nmExpr = (NamedElmExpr)agreeExpr;
			if (nmExpr.getElm() instanceof Arg) {
				Arg nmElmArg = (Arg)nmExpr.getElm();
				System.out.println("nmElmArg_name: "+nmElmArg.getName());
				vdmExpr.setIdentifier(nmElmArg.getName());
			} else if(nmExpr.getElm() instanceof Port) {
				Port nmElmPort = (Port)nmExpr.getElm();
				System.out.println("Port_name: "+nmElmPort.getName());
				vdmExpr.setIdentifier(nmElmPort.getName());
			} else {
				System.out.println("getElm: "+nmExpr.getElm());
				System.out.println("getElm Name: "+nmExpr.getElm().getName());
				vdmExpr.setIdentifier(nmExpr.getElm().getName());
			}
		} else if(agreeExpr instanceof SelectionExpr) {
			System.out.println("Selection expr");
			SelectionExpr selExpr = (SelectionExpr)agreeExpr;
			if(selExpr.getField()!=null) {
				System.out.println("Selection Expr field: "+selExpr.getField());
				if (selExpr.getField() instanceof Property) {
					Property selProp = (Property)selExpr.getField();
					if(selProp.getName()!=null) {
						System.out.println("selProp_name: "+selProp.getName());
					}
					if (selProp.getType()!=null) {
						System.out.println("selProp_type: "+selProp.getType());
					}
					if (!selProp.getAppliesTos().isEmpty()) {
						System.out.println("selProp_appliesto: "+selProp.getAppliesTos());
					}
				}
			}
			System.out.println("Selection Expr target: "+selExpr.getTarget());
			if(selExpr.getTarget()!=null) {
				//TODO: has to be changed once we get the field name
				vdmExpr = getVdmExpressionFromAgreeExpression(selExpr.getTarget());
			}
		} else if (agreeExpr instanceof BinaryExpr) {			
			System.out.println("Binary expr");
			BinaryExpr binExpr = (BinaryExpr)agreeExpr;
			BinaryOperation binoper = new BinaryOperation();//for vdm
			//set left operand
			System.out.println("Binary Expr Left: "+binExpr.getLeft());
			Expression leftOperand = getVdmExpressionFromAgreeExpression(binExpr.getLeft());
			binoper.setLhsOperand(leftOperand);
			//set right operand
			System.out.println("Binary Expr Right: "+binExpr.getRight());
			Expression rightOperand = getVdmExpressionFromAgreeExpression(binExpr.getRight());
			binoper.setRhsOperand(rightOperand);
			//set appropriate operator
			System.out.println("Binary Expr Op: "+binExpr.getOp());
			String operator = binExpr.getOp();			
			if(operator.equalsIgnoreCase("->")) {
				vdmExpr.setArrow(binoper);
			} else if(operator.equalsIgnoreCase("=>")) {
				vdmExpr.setImplies(binoper);
			} else if(operator.equalsIgnoreCase("and")) {
				vdmExpr.setAnd(binoper);
			} else if(operator.equalsIgnoreCase("or")) {
				vdmExpr.setOr(binoper);
			} else if(operator.equalsIgnoreCase("=")) {
				vdmExpr.setEqual(binoper);
			} else if(operator.equalsIgnoreCase(">")) {
				vdmExpr.setGreaterThan(binoper);
			} else if(operator.equalsIgnoreCase("<")) {
				vdmExpr.setLessThan(binoper);
			} else if(operator.equalsIgnoreCase(">=")) {
				vdmExpr.setGreaterThanOrEqualTo(binoper);
			} else if(operator.equalsIgnoreCase("<=")) {
				vdmExpr.setLessThanOrEqualTo(binoper);
			} else if(operator.equalsIgnoreCase("+")) {
				vdmExpr.setPlus(binoper);
			} else if(operator.equalsIgnoreCase("-")) {
				vdmExpr.setMinus(binoper);
			} else if(operator.equalsIgnoreCase("!=")|| operator.equalsIgnoreCase("<>")) {
				vdmExpr.setNotEqual(binoper);
			} else if(operator.equalsIgnoreCase("/")) {
				vdmExpr.setDiv(binoper);
			} else if(operator.equalsIgnoreCase("*")) {
				vdmExpr.setTimes(binoper);
			} else {
				System.out.println("Unmapped binary operator: "+operator);
			}
		} else if (agreeExpr instanceof UnaryExpr) {
			System.out.println("Unary expr");
			UnaryExpr unExpr = (UnaryExpr)agreeExpr;
			Expression singleOperand = getVdmExpressionFromAgreeExpression(unExpr.getExpr());
			String operator = unExpr.getOp();	
			if(operator.equalsIgnoreCase("this")) {
				vdmExpr.setCurrent(singleOperand);
			} else if(operator.equalsIgnoreCase("not")) {
				vdmExpr.setNot(singleOperand);
			} else {
				System.out.println("****************Unmapped unary operator *********.");
			}
		} else if (agreeExpr instanceof BoolLitExpr) {
			System.out.println("BoolLitExpr");
			BoolLitExpr boolExpr = (BoolLitExpr)agreeExpr;			
			vdmExpr.setBoolLiteral(boolExpr.getVal().getValue());
		} else if (agreeExpr instanceof EnumLitExpr) {
			System.out.println("EnumLitExpr");
			EnumLitExpr enumExpr = (EnumLitExpr)agreeExpr;
			System.out.println("enumExpr Value: "+ enumExpr.getValue());
			//TODO: parse type which is an instance of type doubledotref and set the enumtype value accordingly 
			System.out.println("enumExpr Type elm name: "+ enumExpr.getEnumType().getElm());
			vdmExpr.setIdentifier(enumExpr.getValue());//TODO: check if it is ok to keep only value "satellite0" in enum(Data_Types::Constellation, Satellite0)
		} else if (agreeExpr instanceof PreExpr) {
			PreExpr preExpr = (PreExpr)agreeExpr;
			System.out.println("PreExpr : "+preExpr.getExpr());
			Expression expr = getVdmExpressionFromAgreeExpression(preExpr.getExpr());
			vdmExpr.setPre(expr);
		} else if (agreeExpr instanceof RecordLitExpr) {
			RecordLitExpr recLitExpr = (RecordLitExpr)agreeExpr;
			System.out.println("RecordLitExpr Type: "+recLitExpr.getRecordType());
			DoubleDotRef ddref = (DoubleDotRef)recLitExpr.getRecordType();
			System.out.println("RecLitExpr_Type_getelm "+ddref.getElm());
			EList<NamedElement> recLitExprArgs = recLitExpr.getArgs();
			//below are the values set to variable
			for(NamedElement recLitExprArg: recLitExprArgs) {
				System.out.println("RecLitExpr arg "+recLitExprArg);
				//TODO: identify property attributes
			}
			//TODO: could set ExpressionList of RecordProjections
		} else if(agreeExpr instanceof IntLitExpr){
			IntLitExpr intLitExpr = (IntLitExpr)agreeExpr;
			BigInteger bigInt = new BigInteger(intLitExpr.getVal());
			vdmExpr.setIntLiteral(bigInt);			
		} else {
			System.out.println("Other expr");
			System.out.println(agreeExpr.toString());
			//TODO: parse and set vdmExpr accordingly -- also Other expr and unmapped unary and binary operators
		}
		return vdmExpr;
	}
}
