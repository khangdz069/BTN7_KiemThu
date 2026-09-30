using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace N7_btn
{
    [TestClass]
    public class Bai07_SumTests_DDT
    {
        public TestContext TestContext { get; set; }

        [TestMethod]
        [DeploymentItem("data_csv\\Bai07_Sum_data.csv")]
        [DataSource("Microsoft.VisualStudio.TestTools.DataSource.CSV", "|DataDirectory|\\Bai07_Sum_data.csv", "Bai07_Sum_data#csv", DataAccessMethod.Sequential)]
        public void Sum_DataDriven()
        {
            MethodLibrary.MethodLibrary m = new MethodLibrary.MethodLibrary();

            long n = Convert.ToInt64(TestContext.DataRow[0]);
            long exp_k = Convert.ToInt64(TestContext.DataRow[1]);
            long exp_s = Convert.ToInt64(TestContext.DataRow[2]);

            long s;
            long act_k = m.Sum(n, out s);

            Assert.AreEqual(exp_k, act_k);
            Assert.AreEqual(exp_s, s);
        }
    }
}
