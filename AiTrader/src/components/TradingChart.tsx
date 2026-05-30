import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import type { ChartData } from '../types';
import styles from './TradingChart.module.css';

interface TradingChartProps {
  data: ChartData[];
}

export const TradingChart = ({ data }: TradingChartProps) => {
  return (
    <div className={styles.container}>
      <h3 className={styles.title}>BTC/USDT 实时走势</h3>
      <div className={styles.chartWrapper}>
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={data} margin={{ top: 10, right: 20, left: 10, bottom: 0 }}>
            <defs>
              <linearGradient id="btcPriceGradient" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#4CAF50" stopOpacity={0.8}/>
                <stop offset="95%" stopColor="#4CAF50" stopOpacity={0}/>
              </linearGradient>
            </defs>
            <CartesianGrid strokeDasharray="3 3" stroke="#333" vertical={false} />
            <XAxis 
              dataKey="timestamp" 
              stroke="#666" 
              tick={{ fontSize: 12 }}
              interval="preserveStartEnd"
              tickFormatter={(val) => {
                const date = new Date(val);
                return `${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`;
              }}
            />
            <YAxis 
              dataKey="price" 
              stroke="#666" 
              domain={['auto', 'auto']}
              tick={{ fontSize: 12 }}
              tickFormatter={(value) => `$${value.toLocaleString()}`}
              width={80}
            />
            <Tooltip 
              contentStyle={{ backgroundColor: '#252525', border: '1px solid #333', borderRadius: '4px', color: '#fff' }}
              itemStyle={{ color: '#4CAF50' }}
              labelStyle={{ color: '#888', marginBottom: '5px' }}
              labelFormatter={(val) => {
                const date = new Date(val);
                return `${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`;
              }}
              formatter={(value?: number) => [
                `$${value?.toLocaleString() ?? '0'}`, 
                'Price'
              ]}
              cursor={{ stroke: '#666', strokeWidth: 1, strokeDasharray: '4 4' }} 
            />
            <Area 
              type="monotone" 
              dataKey="price" 
              stroke="#4CAF50" 
              strokeWidth={2}
              fillOpacity={1} 
              fill="url(#btcPriceGradient)" 
              activeDot={{ r: 5, fill: '#4CAF50', stroke: '#fff', strokeWidth: 2 }}
              connectNulls={true} // 连接空值点
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
};
