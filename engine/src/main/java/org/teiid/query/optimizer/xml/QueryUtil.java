/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */

package org.teiid.query.optimizer.xml;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.teiid.api.exception.query.QueryMetadataException;
import org.teiid.api.exception.query.QueryPlannerException;
import org.teiid.api.exception.query.QueryResolverException;
import org.teiid.core.TeiidComponentException;
import org.teiid.core.TeiidException;
import org.teiid.core.TeiidProcessingException;
import org.teiid.query.QueryPlugin;
import org.teiid.query.mapping.relational.QueryNode;
import org.teiid.query.metadata.QueryMetadataInterface;
import org.teiid.query.parser.QueryParser;
import org.teiid.query.resolver.QueryResolver;
import org.teiid.query.resolver.util.ResolverUtil;
import org.teiid.query.rewriter.QueryRewriter;
import org.teiid.query.sql.LanguageObject;
import org.teiid.query.sql.lang.Command;
import org.teiid.query.sql.lang.From;
import org.teiid.query.sql.lang.FromClause;
import org.teiid.query.sql.lang.Query;
import org.teiid.query.sql.lang.Select;
import org.teiid.query.sql.navigator.DeepPreOrderNavigator;
import org.teiid.query.sql.symbol.ElementSymbol;
import org.teiid.query.sql.symbol.Expression;
import org.teiid.query.sql.symbol.GroupSymbol;
import org.teiid.query.sql.symbol.MultipleElementSymbol;
import org.teiid.query.sql.symbol.Reference;
import org.teiid.query.sql.symbol.Symbol;
import org.teiid.query.sql.visitor.ElementCollectorVisitor;
import org.teiid.query.sql.visitor.ReferenceCollectorVisitor;
import org.teiid.query.util.CommandContext;



/** 
 * Helper methods for dealing with relational queries while performing XML planning.
 *  
 * @since 4.3
 */
public class QueryUtil {

    /** Parse a query from a query node and return a Command object.
     * @param groupName 
     * 
     * @param queryNode The query node which contains a query
     * @param planEnv The planner environment
     * @return New Command object
     * @throws QueryPlannerException If an error occurred
     * @since 4.3
     */
    static Command getQuery(String groupName, QueryNode queryNode, XMLPlannerEnvironment env) throws QueryPlannerException {
        Command query = queryNode.getCommand();
        
        if (query == null) {
            try {
                query = QueryParser.getQueryParser().parseCommand(queryNode.getQuery());
                QueryResolver.resolveWithBindingMetadata(query, env.getGlobalMetadata().getDesignTimeMetadata(), queryNode, true);
            } catch (TeiidException e) {
                 throw new QueryPlannerException(QueryPlugin.Event.TEIID30281, e, QueryPlugin.Util.gs(QueryPlugin.Event.TEIID30281, new Object[]{groupName, queryNode.getQuery()}));
			}
        } 
        return query;
    }

    /** 
     * Rewrite a command using the metadata in the planner environment.
     * @param query The query to rewrite
     * @param planEnv The planner environment
     * @throws QueryPlannerException
     * @throws TeiidComponentException 
     * @since 4.3
     */
    static Command rewriteQuery(Command query, QueryMetadataInterface metadata, CommandContext context) 
        throws QueryPlannerException, TeiidComponentException {
        try {
            return QueryRewriter.rewrite(query, metadata, context);
        } catch(TeiidProcessingException e) {
             throw new QueryPlannerException(e);
        }
    }

    /**
     * Returns the query node (object holding SQL query transformation) for the
     * indicated table
     * @param groupName String name of a temporary group (a.k.a. temp table)
     * @param metadata QueryMetadataInterface source of metadata
     * @return QueryNode defining the query transformation of the temp table
     * @throws QueryPlannerException for any logical exception detected
     * @throws QueryMetadataException if metadata encounters exception
     * @throws TeiidComponentException unexpected exception
     */
    static QueryNode getQueryNode(String groupName, QueryMetadataInterface metadata)
        throws QueryPlannerException, QueryMetadataException, TeiidComponentException{

        QueryNode queryNode = null;
        try {
            GroupSymbol gs = new GroupSymbol(groupName);
            ResolverUtil.resolveGroup(gs, metadata);
            queryNode = metadata.getVirtualPlan(gs.getMetadataID());
        } catch (QueryResolverException e) {
             throw new QueryPlannerException(QueryPlugin.Event.TEIID30283, e, QueryPlugin.Util.gs(QueryPlugin.Event.TEIID30283, groupName));
        }
        return queryNode;
    }    
    
    static Query wrapQuery(FromClause fromClause, String groupName) {
        Select select = new Select();
        select.addSymbol(new MultipleElementSymbol(groupName));
        Query query = new Query();
        query.setSelect(select);
        From from = new From();
        from.addClause(fromClause);
        query.setFrom(from);        
        return query;
    }

    public static GroupSymbol createResolvedGroup(String groupName, QueryMetadataInterface metadata) 
        throws TeiidComponentException {
        GroupSymbol group = new GroupSymbol(groupName);
        return createResolvedGroup(group, metadata);
    }
    
    public static GroupSymbol createResolvedGroup(GroupSymbol group, QueryMetadataInterface metadata) 
        throws TeiidComponentException {
        try {
            ResolverUtil.resolveGroup(group, metadata);
            return group;
        } catch (QueryResolverException e) {
             throw new TeiidComponentException(QueryPlugin.Event.TEIID30284, e);
        }
    }
        
    static Command getQueryFromQueryNode(String groupName, XMLPlannerEnvironment planEnv) 
        throws QueryPlannerException, QueryMetadataException, TeiidComponentException {
        
        QueryNode queryNode = QueryUtil.getQueryNode(groupName, planEnv.getGlobalMetadata());
        Command command = QueryUtil.getQuery(groupName, queryNode, planEnv);
        return command;
    }     
    
    static void markBindingsAsNonExternal(LanguageObject object,
			Collection<ElementSymbol> allBindings) {
    	List<ElementSymbol> elements = new ArrayList<ElementSymbol>();
    	ElementCollectorVisitor visitor = new ElementCollectorVisitor(elements);
        DeepPreOrderNavigator.doVisit(object, visitor);
        for (Iterator<ElementSymbol> i = elements.iterator(); i.hasNext();) {
        	ElementSymbol elementSymbol = i.next();
			if (allBindings.contains(elementSymbol)) {
				elementSymbol.setIsExternalReference(false);
				elementSymbol.setMetadataID(null);
			}
		}
    }
    
    static Collection<ElementSymbol> getBindingElements(QueryNode queryNode) throws TeiidComponentException {
    	HashSet<ElementSymbol> set = new HashSet<ElementSymbol>();
    	ElementCollectorVisitor.getElements(QueryResolver.parseBindings(queryNode), set);
    	return set;
    }
    
    static List<ElementSymbol> getBindingsReferences(LanguageObject object, Collection<ElementSymbol> allBindings) {
    	List<ElementSymbol> elements = new LinkedList<ElementSymbol>();
    	ElementCollectorVisitor visitor = new ElementCollectorVisitor(elements);
        DeepPreOrderNavigator.doVisit(object, visitor);
        for (Iterator<ElementSymbol> i = elements.iterator(); i.hasNext();) {
        	ElementSymbol elementSymbol = i.next();
			if (!elementSymbol.isExternalReference() || !allBindings.contains(elementSymbol)) {
				i.remove();
			}
		}
    	return elements;
    }
    
    static Map createSymbolMap(GroupSymbol oldGroup, final String newGroup, Collection projectedElements) {
        HashMap symbolMap = new HashMap();
        symbolMap.put(oldGroup, new GroupSymbol(newGroup));

        for (Iterator i = projectedElements.iterator(); i.hasNext();) {
            ElementSymbol element = (ElementSymbol)i.next();

            symbolMap.put(element, new ElementSymbol(newGroup + Symbol.SEPARATOR + element.getShortName()));
        }
        return symbolMap;
    }

    
    static List<Reference> getReferences(Command command) {
        List<Reference> boundList = new ArrayList<Reference>();
        
        for (Iterator<Reference> refs = ReferenceCollectorVisitor.getReferences(command).iterator(); refs.hasNext();) {
            Reference ref = refs.next();
            Expression expr = ref.getExpression();
            if (!(expr instanceof ElementSymbol)){
                continue;
            }
            ElementSymbol elem = (ElementSymbol)expr;
            
            if (!command.getExternalGroupContexts().getGroups().contains(elem.getGroupSymbol())) {
                continue;
            }                
            boundList.add(ref);
        }
        return boundList;
    }

}
