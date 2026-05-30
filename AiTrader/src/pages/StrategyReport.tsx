import { useLocation, useNavigate } from 'react-router-dom';
import ReactMarkdown from 'react-markdown';
import { ArrowLeft, Share2 } from 'lucide-react';

export const StrategyReport = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const content = location.state?.answer || location.state?.content || '';

  const handleBack = () => {
    navigate(-1);
  };

  return (
    <div style={{ 
      backgroundColor: '#f5f5f5', 
      minHeight: '100vh', 
      display: 'flex', 
      flexDirection: 'column' 
    }}>
      {/* Header */}
      <div style={{ 
        backgroundColor: '#fff', 
        padding: '16px', 
        display: 'flex', 
        alignItems: 'center', 
        gap: '12px',
        borderBottom: '1px solid #eee',
        position: 'sticky',
        top: 0,
        zIndex: 10
      }}>
        <button 
          onClick={handleBack}
          style={{ 
            background: 'none', 
            border: 'none', 
            padding: '4px',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center'
          }}
        >
          <ArrowLeft size={24} color="#333" />
        </button>
        <h1 style={{ 
          fontSize: '18px', 
          fontWeight: '600', 
          margin: 0, 
          flex: 1 
        }}>
          Strategy Report
        </h1>
        <button style={{ background: 'none', border: 'none', padding: '4px' }}>
          <Share2 size={20} color="#666" />
        </button>
      </div>

      {/* Content */}
      <div style={{ 
        padding: '20px', 
        flex: 1, 
        overflowY: 'auto',
        backgroundColor: '#fff',
        maxWidth: '800px',
        margin: '0 auto',
        width: '100%',
        boxSizing: 'border-box'
      }}>
        {content ? (
          <div className="markdown-content">
            <ReactMarkdown>{content}</ReactMarkdown>
          </div>
        ) : (
          <div style={{ 
            textAlign: 'center', 
            color: '#999', 
            marginTop: '40px' 
          }}>
            No report content available.
          </div>
        )}
      </div>
      
      <style>{`
        .markdown-content {
          color: #333;
          line-height: 1.6;
          font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans', 'Helvetica Neue', sans-serif;
        }
        .markdown-content h1 { font-size: 24px; margin-bottom: 16px; color: #1a1a1a; }
        .markdown-content h2 { font-size: 20px; margin-top: 24px; margin-bottom: 12px; color: #2c3e50; border-bottom: 1px solid #eee; padding-bottom: 8px; }
        .markdown-content h3 { font-size: 18px; margin-top: 20px; margin-bottom: 10px; color: #34495e; }
        .markdown-content ul, .markdown-content ol { padding-left: 20px; margin-bottom: 16px; }
        .markdown-content li { margin-bottom: 8px; }
        .markdown-content p { margin-bottom: 16px; }
        .markdown-content strong { color: #2c3e50; font-weight: 600; }
        .markdown-content blockquote { border-left: 4px solid #8884d8; padding-left: 16px; margin: 16px 0; color: #555; background: #f8f9fa; padding: 12px 16px; border-radius: 0 4px 4px 0; }
        .markdown-content code { background: #f1f1f1; padding: 2px 4px; border-radius: 4px; font-family: monospace; font-size: 0.9em; }
      `}</style>
    </div>
  );
};
